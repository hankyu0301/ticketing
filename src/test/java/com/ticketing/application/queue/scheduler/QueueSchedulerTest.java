package com.ticketing.application.queue.scheduler;

import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.application.queue.scheduler.QueueScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set; // Set 임포트 필수

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueSchedulerTest {

    @InjectMocks
    private QueueScheduler queueScheduler;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Test
    @DisplayName("스케줄러를 실행하면 입장 처리 로직을 호출한다.")
    void processQueueSuccess() {
        // given
        // 1. 관리 대상 스케줄이 있다고 가정해야 함
        given(queueRedisRepository.getProcessingSchedules())
                .willReturn(Set.of(1L));

        // 2. 입장한 유저가 있다고 가정
        given(queueRedisRepository.moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(List.of("1", "2", "3"));

        // when
        queueScheduler.processQueue();

        // then
        // Repository의 통합 Lua Script 메서드가 호출되었는지 검증
        verify(queueRedisRepository, times(1))
                .moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }
}
