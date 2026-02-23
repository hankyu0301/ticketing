package com.ticketing.infrastructure.lock.repository;

import com.ticketing.infrastructure.lock.mapper.NamedLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "NAMED")
public class NamedLockRepository {

    private final NamedLockMapper namedLockMapper;

    public void getLock(String lockName) {
        Integer result = namedLockMapper.getLock(lockName, 3);
        checkResult(result, "NamedLock 획득 실패: " + lockName);
    }

    public void releaseLock(String lockName) {
        Integer result = namedLockMapper.releaseLock(lockName);
        checkResult(result, "NamedLock 해제 실패: " + lockName);
    }

    private void checkResult(Integer result, String message) {
        // 1(성공)이 아니면 에러 처리
        if (result == null || result != 1) {
            throw new IllegalStateException(message);
        }
    }
}