package com.ticketing.domain.reservation.executor;

import com.ticketing.domain.lock.repository.RedisLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "LETTUCE")
public class LettuceLockExecutor {

    private final RedisLockRepository redisLockRepository;

    public <T> T execute(Long key, Supplier<T> supplier) {
        while (!redisLockRepository.lock(key)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Lock 획득 대기 중 인터럽트 발생", e);
            }
        }
        try {
            return supplier.get();
        } finally {
            redisLockRepository.unlock(key);
        }
    }
}