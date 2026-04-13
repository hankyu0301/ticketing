package com.ticketing.application.queue.service;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.application.queue.service.QueueService;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
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

    @Autowired
    private TicketingTestDataSetup testDataSetup;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private static final int THREAD_COUNT = 100;
    private long scheduleId;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        TicketingTestDataSetup.TestData testData = testDataSetup.setupConcertAndSeat();
        this.scheduleId = testData.scheduleId();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.execute();
    }

    @Test
    @DisplayName("100명이 동시에 접속하면 10명은 즉시 입장하고 90명은 정확한 순번을 받는다.")
    void concurrentEnterQueueTest() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        Set<Long> ranks = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            long memberId = i + 1;
            executorService.submit(() -> {
                try {
                    QueueResponse response = queueService.enterQueue(scheduleId, memberId);
                    if (!response.isEntered()) {
                        ranks.add(response.getRank());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        Long activeCount = redisTemplate.opsForZSet().zCard(getActiveKey());
        assertThat(activeCount).isEqualTo(10L);

        Set<String> waitingMembers = redisTemplate.opsForZSet().range(getWaitKey(), 0, -1);
        assertThat(waitingMembers).isNotNull();
        assertThat(waitingMembers.size()).isEqualTo(90);

        assertThat(ranks).hasSize(90);
        assertThat(Collections.max(ranks)).isEqualTo(90L);
    }

    private String getActiveKey() {
        return "queue:{" + scheduleId + "}:active";
    }

    private String getWaitKey() {
        return "queue:{" + scheduleId + "}:wait";
    }
}
