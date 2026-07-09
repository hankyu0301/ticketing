package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.exception.LockAcquisitionFailureException;
import com.ticketing.infrastructure.lock.repository.RedisLockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LettuceLockExecutorTest {

    @Mock
    private RedisLockRepository redisLockRepository;

    @InjectMocks
    private LettuceLockExecutor lettuceLockExecutor;

    @AfterEach
    void tearDown() {
        lettuceLockExecutor.shutdownHeartbeatExecutor();
    }

    @Test
    @DisplayName("Lettuce 락 획득 성공 시 작업 결과를 반환하고 락을 해제한다")
    void execute_success_unlocksAfterExecution() {
        ReflectionTestUtils.setField(lettuceLockExecutor, "waitTimeMs", 1000L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "retryIntervalMs", 10L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "leaseTimeMs", 3000L);
        given(redisLockRepository.lock(anyString(), anyString(), any())).willReturn(true);
        given(redisLockRepository.unlock(anyString(), anyString())).willReturn(true);

        String result = lettuceLockExecutor.execute("seat:1", () -> "reserved");

        assertThat(result).isEqualTo("reserved");
        verify(redisLockRepository).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("Lettuce 락을 지정 시간 안에 얻지 못하면 락 획득 실패 예외를 던진다")
    void execute_fail_whenLockAcquisitionTimeout() {
        ReflectionTestUtils.setField(lettuceLockExecutor, "waitTimeMs", 1L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "retryIntervalMs", 1L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "leaseTimeMs", 3000L);
        given(redisLockRepository.lock(anyString(), anyString(), any())).willReturn(false);

        assertThatThrownBy(() -> lettuceLockExecutor.execute("seat:1", () -> "reserved"))
                .isInstanceOf(LockAcquisitionFailureException.class);
    }

    @Test
    @DisplayName("Lettuce heartbeat 갱신이 실패하면 작업을 실패로 처리한다")
    void execute_fail_whenHeartbeatLosesLock() {
        ReflectionTestUtils.setField(lettuceLockExecutor, "waitTimeMs", 1000L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "retryIntervalMs", 10L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "leaseTimeMs", 1500L);
        given(redisLockRepository.lock(anyString(), anyString(), any())).willReturn(true);
        given(redisLockRepository.renew(anyString(), anyString(), any())).willReturn(false);
        given(redisLockRepository.unlock(anyString(), anyString())).willReturn(true);

        AtomicBoolean interrupted = new AtomicBoolean(false);

        assertThatThrownBy(() -> lettuceLockExecutor.execute("seat:1", () -> {
            try {
                while (true) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("락 소유권을 상실하여 작업 결과를 신뢰할 수 없습니다.");

        assertThat(interrupted.get()).isTrue();
        verify(redisLockRepository).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("Lettuce heartbeat 갱신 중 예외가 발생하면 락 유실로 처리해야 한다")
    void execute_fail_whenHeartbeatRenewThrowsException() {
        ReflectionTestUtils.setField(lettuceLockExecutor, "waitTimeMs", 1000L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "retryIntervalMs", 10L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "leaseTimeMs", 1500L);
        CountDownLatch renewAttempted = new CountDownLatch(1);
        given(redisLockRepository.lock(anyString(), anyString(), any())).willReturn(true);
        given(redisLockRepository.renew(anyString(), anyString(), any()))
                .willAnswer(invocation -> {
                    renewAttempted.countDown();
                    throw new RuntimeException("redis timeout");
                });
        given(redisLockRepository.unlock(anyString(), anyString())).willReturn(true);

        AtomicBoolean interrupted = new AtomicBoolean(false);

        assertThatThrownBy(() -> lettuceLockExecutor.execute("seat:1", () -> {
            try {
                assertThat(renewAttempted.await(2, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(200L);
                return "reserved";
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("락 소유권을 상실하여 작업 결과를 신뢰할 수 없습니다.");

        assertThat(interrupted.get()).isTrue();
        verify(redisLockRepository).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("Lettuce 락 획득 후 heartbeat 등록에 실패해도 락을 해제해야 한다")
    void execute_unlocks_whenHeartbeatSchedulingFailsAfterLockAcquired() {
        ReflectionTestUtils.setField(lettuceLockExecutor, "waitTimeMs", 1000L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "retryIntervalMs", 10L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "leaseTimeMs", 1500L);
        ScheduledThreadPoolExecutor failingHeartbeatExecutor = mock(ScheduledThreadPoolExecutor.class);
        given(failingHeartbeatExecutor.scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)
        )).willThrow(new RejectedExecutionException("heartbeat scheduler stopped"));
        ReflectionTestUtils.setField(lettuceLockExecutor, "heartbeatExecutor", failingHeartbeatExecutor);
        given(redisLockRepository.lock(anyString(), anyString(), any())).willReturn(true);
        given(redisLockRepository.unlock(anyString(), anyString())).willReturn(true);

        assertThatThrownBy(() -> lettuceLockExecutor.execute("seat:1", () -> "reserved"))
                .isInstanceOf(RejectedExecutionException.class);

        verify(redisLockRepository).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("Lettuce heartbeat 하나가 지연되어도 다른 락의 heartbeat 갱신은 병렬로 실행된다")
    void execute_renewsOtherLocksWhenOneHeartbeatIsBlocked() throws Exception {
        ReflectionTestUtils.setField(lettuceLockExecutor, "waitTimeMs", 1000L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "retryIntervalMs", 10L);
        ReflectionTestUtils.setField(lettuceLockExecutor, "leaseTimeMs", 1500L);
        CountDownLatch blockingRenewStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockingRenew = new CountDownLatch(1);
        CountDownLatch fastRenewed = new CountDownLatch(1);
        CountDownLatch finishBlockingSupplier = new CountDownLatch(1);
        given(redisLockRepository.lock(anyString(), anyString(), any())).willReturn(true);
        given(redisLockRepository.renew(anyString(), anyString(), any()))
                .willAnswer(invocation -> {
                    String lockName = invocation.getArgument(0);
                    if ("seat:blocking".equals(lockName)) {
                        blockingRenewStarted.countDown();
                        awaitLatch(releaseBlockingRenew, 3, TimeUnit.SECONDS, "blocking renewal release");
                    }
                    if ("seat:fast".equals(lockName)) {
                        fastRenewed.countDown();
                    }
                    return true;
                });
        given(redisLockRepository.unlock(anyString(), anyString())).willReturn(true);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> blockingFuture = workers.submit(() ->
                    lettuceLockExecutor.execute("seat:blocking", () -> {
                        awaitLatch(finishBlockingSupplier, 3, TimeUnit.SECONDS, "blocking supplier finish");
                        return "blocking";
                    })
            );

            awaitLatch(blockingRenewStarted, 2, TimeUnit.SECONDS, "blocking renewal start");

            Future<String> fastFuture = workers.submit(() ->
                    lettuceLockExecutor.execute("seat:fast", () -> {
                        awaitLatch(fastRenewed, 2, TimeUnit.SECONDS, "fast renewal");
                        return "fast";
                    })
            );

            awaitLatch(fastRenewed, 2, TimeUnit.SECONDS, "fast renewal should not be blocked");
            releaseBlockingRenew.countDown();
            finishBlockingSupplier.countDown();

            assertThat(fastFuture.get(2, TimeUnit.SECONDS)).isEqualTo("fast");
            assertThat(blockingFuture.get(2, TimeUnit.SECONDS)).isEqualTo("blocking");
        } finally {
            releaseBlockingRenew.countDown();
            finishBlockingSupplier.countDown();
            workers.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch, long timeout, TimeUnit unit, String description) {
        try {
            assertThat(latch.await(timeout, unit)).as(description).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " interrupted", e);
        }
    }
}
