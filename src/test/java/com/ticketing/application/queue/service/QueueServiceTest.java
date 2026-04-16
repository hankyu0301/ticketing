package com.ticketing.application.queue.service;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.global.common.exception.queue.QueueBadRequestException;
import com.ticketing.global.common.exception.queue.QueueConfigException;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.infrastructure.persistence.queue.QueueScheduleMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private QueueScheduleMetaLoader queueScheduleMetaLoader;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "estimatedThroughput", 50L);
        ReflectionTestUtils.setField(queueService, "maxActiveMember", 1000L);
        ReflectionTestUtils.setField(queueService, "activeTimeMs", 300000L);
    }

    @Test
    @DisplayName("warm path immediate entry는 loader 없이 바로 처리한다")
    void enterQueueFreePass() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-100L);

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
        verify(queueScheduleMetaLoader, never()).getOrLoad(anyLong());
        verify(queueRedisRepository, never()).addProcessingSchedule(anyLong());
    }

    @Test
    @DisplayName("warm path waiting entry는 rank를 반환하고 processing schedule을 추가한다")
    void enterQueueWaiting() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(4L);

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.getRank()).isEqualTo(5L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
        verify(queueScheduleMetaLoader, never()).getOrLoad(anyLong());
        verify(queueRedisRepository).addProcessingSchedule(1L);
    }

    @Test
    @DisplayName("meta miss면 loader로 복구한 뒤 다시 queue entry를 시도한다")
    void enterQueueRecoversMetaOnMiss() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-2L, 4L);
        given(queueScheduleMetaLoader.getOrLoad(1L)).willReturn(Optional.of(openedScheduleMeta()));

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.getRank()).isEqualTo(5L);
        verify(queueScheduleMetaLoader).getOrLoad(1L);
        verify(queueRedisRepository).addProcessingSchedule(1L);
    }

    @Test
    @DisplayName("meta miss 복구에 실패하면 스케줄 없음 예외를 반환한다")
    void enterQueueFailScheduleNotFound() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-2L);
        given(queueScheduleMetaLoader.getOrLoad(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.enterQueue(999L, 1L))
                .isInstanceOf(ConcertScheduleNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
    }

    @Test
    @DisplayName("reservation not open 코드가 오면 예외로 매핑한다")
    void enterQueueFailBeforeReservationOpen() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-3L);

        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueBadRequestException.class)
                .hasMessage(QueueErrorMessage.RESERVATION_NOT_OPEN);
    }

    @Test
    @DisplayName("queue entry 결과가 not found면 queue 예외를 반환한다")
    void enterQueueFailError() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-1L);

        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    @Test
    @DisplayName("warm path active status는 loader 없이 바로 처리한다")
    void getQueueStatusActive() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(-100L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
        verify(queueScheduleMetaLoader, never()).getOrLoad(anyLong());
    }

    @Test
    @DisplayName("warm path waiting status는 rank와 retryAfter를 반환한다")
    void getQueueStatusWaiting() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(49L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.isEntered()).isFalse();
        assertThat(response.getRank()).isEqualTo(50L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
        verify(queueScheduleMetaLoader, never()).getOrLoad(anyLong());
    }

    @Test
    @DisplayName("queue status에서 meta miss면 loader로 복구 후 재조회한다")
    void getQueueStatusRecoversMetaOnMiss() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(-2L, 49L);
        given(queueScheduleMetaLoader.getOrLoad(1L)).willReturn(Optional.of(openedScheduleMeta()));

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.getRank()).isEqualTo(50L);
        verify(queueScheduleMetaLoader).getOrLoad(1L);
    }

    @Test
    @DisplayName("queue status에서 meta miss 복구에 실패하면 스케줄 없음 예외를 반환한다")
    void getQueueStatusFailScheduleNotFound() {
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(-2L);
        given(queueScheduleMetaLoader.getOrLoad(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.getQueueStatus(999L, 1L))
                .isInstanceOf(ConcertScheduleNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
    }

    @Test
    @DisplayName("queue status에서 reservation not open 코드를 예외로 매핑한다")
    void getQueueStatusFailBeforeReservationOpen() {
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(-3L);

        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueBadRequestException.class)
                .hasMessage(QueueErrorMessage.RESERVATION_NOT_OPEN);
    }

    @Test
    @DisplayName("queue status 결과가 not found면 queue 예외를 반환한다")
    void getQueueStatusFailNotFound() {
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(-1L);

        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    @Test
    @DisplayName("retryAfter는 최대 60초로 제한한다")
    void getQueueStatusWaitingMaxRetryTime() {
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(4999L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.getRetryAfter()).isEqualTo(60L);
    }

    @Test
    @DisplayName("estimated throughput이 0이면 상태를 계산할 수 없다")
    void getQueueStatusFailInvalidEstimatedThroughput() {
        ReflectionTestUtils.setField(queueService, "estimatedThroughput", 0L);
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(1L);

        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueConfigException.class)
                .hasMessage(QueueErrorMessage.INVALID_ESTIMATED_THROUGHPUT);
    }

    @Test
    @DisplayName("active leave는 repository remove만 호출하고 예외 없이 종료된다")
    void leaveActiveSuccess() {
        queueService.leaveActive(1L, 1L);

        verify(queueRedisRepository).removeActiveMember(1L, 1L);
    }

    private QueueScheduleMeta openedScheduleMeta() {
        long now = System.currentTimeMillis();
        return new QueueScheduleMeta(1L, now - 60_000, now + 86_400_000);
    }
}
