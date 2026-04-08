package com.ticketing.infrastructure.lock.repository;

import com.ticketing.infrastructure.lock.mapper.NamedLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "NAMED")
public class NamedLockRepository {

    private final NamedLockMapper namedLockMapper;

    @Value("${ticketing.lock.wait-time-ms:10000}")
    private long waitTimeMs;

    public void getLock(String lockName) {
        Integer result = namedLockMapper.getLock(lockName, resolveTimeoutSeconds());
        checkResult(result, "NamedLock 락 획득 실패: " + lockName);
    }

    public void releaseLock(String lockName) {
        Integer result = namedLockMapper.releaseLock(lockName);
        checkResult(result, "NamedLock 락 해제 실패: " + lockName);
    }

    private void checkResult(Integer result, String message) {
        if (result == null || result != 1) {
            throw new IllegalStateException(message);
        }
    }

    private int resolveTimeoutSeconds() {
        return Math.max(1, (int) Math.ceil(waitTimeMs / 1000.0));
    }
}
