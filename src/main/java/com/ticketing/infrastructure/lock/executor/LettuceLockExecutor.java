package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.exception.LockAcquisitionFailureException;
import com.ticketing.infrastructure.lock.repository.RedisLockRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "LETTUCE")
public class LettuceLockExecutor implements LockExecutor {

    private static final String LOCK_ACQUISITION_TIMEOUT_MESSAGE = "지정한 시간 안에 락을 획득하지 못했습니다.";
    private static final String LOCK_WAIT_INTERRUPTED_MESSAGE = "락 획득 대기 중 인터럽트가 발생했습니다.";
    private static final String LOCK_LOST_MESSAGE = "락 소유권을 상실하여 작업 결과를 신뢰할 수 없습니다.";
    private static final int HEARTBEAT_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());

    private final RedisLockRepository redisLockRepository;
    private final ScheduledThreadPoolExecutor heartbeatExecutor = createHeartbeatExecutor();

    @Value("${ticketing.lock.wait-time-ms:10000}")
    private long waitTimeMs;

    @Value("${ticketing.lock.retry-interval-ms:100}")
    private long retryIntervalMs;

    @Value("${ticketing.lock.lease-time-ms:3000}")
    private long leaseTimeMs;

    @Override
    public <T> T execute(String lockName, Supplier<T> supplier) {
        String token = UUID.randomUUID().toString();
        Duration leaseTime = Duration.ofMillis(leaseTimeMs);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitTimeMs);

        while (!Boolean.TRUE.equals(redisLockRepository.lock(lockName, token, leaseTime))) {
            if (System.nanoTime() >= deadline) {
                throw new LockAcquisitionFailureException(LOCK_ACQUISITION_TIMEOUT_MESSAGE);
            }

            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(LOCK_WAIT_INTERRUPTED_MESSAGE, e);
            }
        }

        Thread executionThread = Thread.currentThread();
        AtomicBoolean lockLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = null;

        try {
            heartbeat = startHeartbeat(
                    lockName,
                    token,
                    leaseTime,
                    executionThread,
                    lockLost
            );
            T result = supplier.get();
            if (lockLost.get()) {
                throw new IllegalStateException(LOCK_LOST_MESSAGE);
            }
            return result;
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(true);
            }
            if (lockLost.get()) {
                Thread.interrupted();
            }
            if (!redisLockRepository.unlock(lockName, token)) {
                log.warn("락 해제에 실패했거나 이미 다른 요청이 소유권을 가져갔습니다. lockName={}", lockName);
            }
        }
    }

    private ScheduledFuture<?> startHeartbeat(
            String lockName,
            String token,
            Duration leaseTime,
            Thread executionThread,
            AtomicBoolean lockLost
    ) {
        long heartbeatIntervalMs = Math.max(leaseTime.toMillis() / 3, 500L);

        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (lockLost.get()) {
                return;
            }

            try {
                boolean renewed = redisLockRepository.renew(lockName, token, leaseTime);
                if (!renewed) {
                    markLockLost(lockName, executionThread, lockLost, null);
                }
            } catch (Exception e) {
                markLockLost(lockName, executionThread, lockLost, e);
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void markLockLost(String lockName, Thread executionThread, AtomicBoolean lockLost, Exception cause) {
        if (lockLost.compareAndSet(false, true)) {
            if (cause == null) {
                log.error("락 갱신에 실패했습니다. 현재 작업 스레드를 중단합니다. lockName={}", lockName);
            } else {
                log.error("락 갱신 중 예외가 발생했습니다. 현재 작업 스레드를 중단합니다. lockName={}", lockName, cause);
            }
            executionThread.interrupt();
        }
    }

    private ScheduledThreadPoolExecutor createHeartbeatExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                HEARTBEAT_POOL_SIZE,
                new LockHeartbeatThreadFactory()
        );
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    private static final class LockHeartbeatThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "lettuce-lock-heartbeat-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
