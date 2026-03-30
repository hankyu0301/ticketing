package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.repository.RedisLockRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "LETTUCE")
public class LettuceLockExecutor implements LockExecutor {

    private final RedisLockRepository redisLockRepository;
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(new LockHeartbeatThreadFactory());

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
                throw new IllegalArgumentException("지정한 시간 안에 락을 획득하지 못했습니다.");
            }

            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("락 획득 대기 중 인터럽트가 발생했습니다.", e);
            }
        }

        ScheduledFuture<?> heartbeat = startHeartbeat(lockName, token, leaseTime);
        try {
            return supplier.get();
        } finally {
            heartbeat.cancel(true);
            if (!redisLockRepository.unlock(lockName, token)) {
                log.warn("락 해제에 실패했거나 이미 다른 요청이 소유권을 가져갔습니다. lockName={}", lockName);
            }
        }
    }

    private ScheduledFuture<?> startHeartbeat(String lockName, String token, Duration leaseTime) {
        long heartbeatIntervalMs = Math.max(leaseTime.toMillis() / 3, 500L);

        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            boolean renewed = redisLockRepository.renew(lockName, token, leaseTime);
            if (!renewed) {
                log.warn("락 갱신에 실패했습니다. lockName={}", lockName);
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    private static final class LockHeartbeatThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "lettuce-lock-heartbeat");
            thread.setDaemon(true);
            return thread;
        }
    }
}
