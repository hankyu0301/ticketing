package com.ticketing.domain.queue;

import com.ticketing.domain.queue.dto.QueueResponse;
import com.ticketing.domain.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "queue.max-active-member=10")
@Testcontainers
class QueueConcurrencyTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379);

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int THREAD_COUNT = 100;
    private static final long SCHEDULE_ID = 1L;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("100명이 동시에 접속했을 때, 10명은 즉시 입장하고 90명은 정확한 순번을 받는다.")
    void concurrent_enter_queue_test() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        Set<Long> ranks = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            long memberId = i + 1;
            executorService.submit(() -> {
                try {
                    QueueResponse response = queueService.enterQueue(SCHEDULE_ID, memberId);
                    if (response.isEntered()) {
                        System.out.println("User " + memberId + " -> Active (예매 활성화)");
                    } else {
                        ranks.add(response.getRank());
                        System.out.println("User " + memberId + " -> Wait Rank: " + response.getRank());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        // 1. 에러 없음 확인
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        // 2. Redis 상태 검증
        Long activeCount = redisTemplate.opsForZSet().zCard(getActiveKey());
        assertThat(activeCount).isEqualTo(10L);

        // 3. 대기열 검증 (90명)
        Set<String> waitingMembers = redisTemplate.opsForZSet().range(getWaitKey(), 0, -1);

        assertThat(waitingMembers).isNotNull();
        assertThat(waitingMembers.size()).isEqualTo(90);

        // 4. 순번 검증
        assertThat(ranks).hasSize(90);
        assertThat(Collections.max(ranks)).isEqualTo(90L);
    }

    private String getActiveKey() { return "queue:{"+SCHEDULE_ID+"}:active"; }
    private String getWaitKey() { return "queue:{"+SCHEDULE_ID+"}:wait"; }
}