package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.exception.LockAcquisitionFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedissonLockExecutorTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private RedissonLockExecutor redissonLockExecutor;

    @Test
    @DisplayName("Redisson 락 획득 성공 시 작업 실행 후 락을 해제한다")
    void execute_success_unlocksAfterExecution() throws Exception {
        ReflectionTestUtils.setField(redissonLockExecutor, "waitTimeMs", 10000L);
        given(redissonClient.getLock("seat:1")).willReturn(lock);
        given(lock.tryLock(10000L, TimeUnit.MILLISECONDS)).willReturn(true);
        given(lock.isLocked()).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        String result = redissonLockExecutor.execute("seat:1", () -> "reserved");

        assertThat(result).isEqualTo("reserved");
        verify(lock).unlock();
    }

    @Test
    @DisplayName("Redisson 락을 얻지 못하면 예외를 던진다")
    void execute_fail_whenLockUnavailable() throws Exception {
        ReflectionTestUtils.setField(redissonLockExecutor, "waitTimeMs", 10000L);
        given(redissonClient.getLock("seat:1")).willReturn(lock);
        given(lock.tryLock(10000L, TimeUnit.MILLISECONDS)).willReturn(false);
        given(lock.isLocked()).willReturn(false);

        assertThatThrownBy(() -> redissonLockExecutor.execute("seat:1", () -> "reserved"))
                .isInstanceOf(LockAcquisitionFailureException.class);
    }
}
