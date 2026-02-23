package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.repository.NamedLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "NAMED")
public class NamedLockExecutor implements LockExecutor {

    private final NamedLockRepository namedLockRepository;

    @Transactional
    @Override
    public <T> T execute(String lockName, Supplier<T> supplier) {
        try{
            namedLockRepository.getLock(lockName);
            return supplier.get();
        } finally {
            namedLockRepository.releaseLock(lockName);
        }
    }
}
