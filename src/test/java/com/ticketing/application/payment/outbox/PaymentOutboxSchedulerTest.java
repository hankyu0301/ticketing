package com.ticketing.application.payment.outbox;

import com.ticketing.global.config.PaymentOutboxExecutionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentOutboxSchedulerTest {
    @Test
    void boundsConcurrencyWithoutQueuingOrRunningRejectedTasks() throws Exception {
        PaymentOutboxProperties properties = new PaymentOutboxProperties();
        properties.setWorkerCount(2);
        ThreadPoolTaskExecutor executor = new PaymentOutboxExecutionConfig().paymentOutboxExecutor(properties);
        executor.initialize();
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(relay).runOnce();
        PaymentOutboxScheduler scheduler = new PaymentOutboxScheduler(executor, relay, properties);
        try {
            scheduler.dispatch();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.dispatch();
            assertThat(calls).hasValue(2);
            assertThat(executor.getQueueSize()).isZero();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void stopsSubmittingOnContextClose() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        PaymentOutboxScheduler scheduler = new PaymentOutboxScheduler(executor, relay, new PaymentOutboxProperties());
        scheduler.stopDispatching();
        scheduler.dispatch();
        verifyNoInteractions(executor, relay);
    }

    @Test
    void interruptsUnfinishedWorkerAfterShutdownDeadline() throws Exception {
        ThreadPoolTaskExecutor executor = new PaymentOutboxExecutionConfig()
                .paymentOutboxExecutor(new PaymentOutboxProperties());
        executor.setAwaitTerminationSeconds(1);
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
        });
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
