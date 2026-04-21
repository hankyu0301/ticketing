package com.ticketing.global.config;

import com.ticketing.application.queue.scheduler.QueueScheduler;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShedLockTest {

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private LockProvider lockProvider;

    private QueueScheduler queueScheduler;
    private DefaultLockingTaskExecutor lockingTaskExecutor;

    @BeforeEach
    void setUp() {
        queueScheduler = new QueueScheduler(queueRedisRepository);
        ReflectionTestUtils.setField(queueScheduler, "maxActiveMember", 1000L);
        ReflectionTestUtils.setField(queueScheduler, "enterBatchSize", 50L);
        ReflectionTestUtils.setField(queueScheduler, "activeTimeMs", 300000L);

        lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
    }

    @Test
    @DisplayName("다른 서버가 이미 락을 잡고 있으면 로직은 실행되지 않는다.")
    void distributedLockSimulation() {
        // given
        given(lockProvider.lock(any())).willReturn(Optional.empty());

        // when
        lockingTaskExecutor.executeWithLock((Runnable) queueScheduler::processQueue, lockConfiguration());

        // then
        verify(queueRedisRepository, never()).getDueProcessingSchedules(anyLong());
        verify(queueRedisRepository, never()).moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("락이 없으면 로직이 실행된다.")
    void normalExecutionTest() {
        // given
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
        given(queueRedisRepository.getDueProcessingSchedules(anyLong())).willReturn(List.of(1L));
        given(queueRedisRepository.moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(Collections.emptyList());

        // when
        lockingTaskExecutor.executeWithLock((Runnable) queueScheduler::processQueue, lockConfiguration());

        // then
        verify(queueRedisRepository, times(1)).getDueProcessingSchedules(anyLong());
        verify(queueRedisRepository, times(1))
                .moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }

    private LockConfiguration lockConfiguration() {
        return new LockConfiguration(
                Instant.now(),
                "QueueScheduler_processQueue",
                Duration.ofSeconds(10),
                Duration.ofSeconds(1)
        );
    }
}
