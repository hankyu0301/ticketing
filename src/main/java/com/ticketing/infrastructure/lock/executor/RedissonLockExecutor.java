package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.exception.LockAcquisitionFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "REDISSON")
public class RedissonLockExecutor implements LockExecutor {

    private final RedissonClient redissonClient;

    @Value("${ticketing.lock.wait-time-ms:10000}")
    private long waitTimeMs;

    @Override
    public <T> T execute(String lockName, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockName);

        try {
            boolean available = lock.tryLock(waitTimeMs, TimeUnit.MILLISECONDS);

            if (!available) {
                log.warn("Redisson lock acquisition failed: {}", lockName);
                throw new LockAcquisitionFailureException("Redisson 락 획득 실패: " + lockName);
            }

            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Redisson 락 획득 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
