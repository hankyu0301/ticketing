package com.ticketing.application.queue.scheduler;

import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueSchedulerTest {

    @InjectMocks
    private QueueScheduler queueScheduler;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Test
    @DisplayName("due schedule 이 있으면 승격 로직을 호출한다.")
    void processQueueSuccess() {
        given(queueRedisRepository.getDueProcessingSchedules(anyLong()))
                .willReturn(List.of(1L));
        given(queueRedisRepository.moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(List.of("1", "2", "3"));

        queueScheduler.processQueue();

        verify(queueRedisRepository, times(1))
                .moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("due schedule 이 없으면 승격 로직을 호출하지 않는다.")
    void processQueueWithoutDueSchedules() {
        given(queueRedisRepository.getDueProcessingSchedules(anyLong()))
                .willReturn(List.of());

        queueScheduler.processQueue();

        verify(queueRedisRepository, never())
                .moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }
}
