package com.ticketing.application.queue.service;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.application.queue.service.QueueService;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "queue.max-active-member=10")
class QueueConcurrencyTest extends IntegrationTestSupport {

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
        databaseCleaner.execute();
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

        try {
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

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        } finally {
            executorService.shutdownNow();
        }

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
