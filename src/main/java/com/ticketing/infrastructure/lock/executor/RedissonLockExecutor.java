package com.ticketing.infrastructure.lock.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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

    @Override
    public <T> T execute(String lockName, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockName);

        try {
            // 락 획득 시도 (최대 10초 대기, 1초 점유)
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);

            if (!available) {
                log.warn("Redisson Lock 획득 실패: {}", lockName);
                throw new IllegalArgumentException("현재 락을 획득할 수 없습니다.");
            }

            // 비즈니스 로직 실행
            return supplier.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Redisson Lock 획득 중 인터럽트 발생", e);
        } finally {
            // 락 해제 (현재 스레드가 락을 가지고 있을 때만)
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}