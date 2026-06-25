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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
}
