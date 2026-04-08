package com.ticketing.infrastructure.lock.executor;

import com.ticketing.infrastructure.lock.repository.NamedLockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NamedLockExecutorTest {

    @Mock
    private NamedLockRepository namedLockRepository;

    @InjectMocks
    private NamedLockExecutor namedLockExecutor;

    @Test
    @DisplayName("NamedLock은 락 획득 성공 후 작업 실행이 끝나면 해제한다")
    void execute_success_releasesLock() {
        String result = namedLockExecutor.execute("seat:1", () -> "reserved");

        assertThat(result).isEqualTo("reserved");
        verify(namedLockRepository).getLock("seat:1");
        verify(namedLockRepository).releaseLock("seat:1");
    }

    @Test
    @DisplayName("NamedLock은 락 획득에 실패하면 해제를 시도하지 않는다")
    void execute_fail_whenAcquireFails() {
        doThrow(new IllegalStateException("lock failed"))
                .when(namedLockRepository)
                .getLock("seat:1");

        assertThatThrownBy(() -> namedLockExecutor.execute("seat:1", () -> "reserved"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lock failed");

        verify(namedLockRepository).getLock("seat:1");
        verify(namedLockRepository, never()).releaseLock("seat:1");
    }
}
