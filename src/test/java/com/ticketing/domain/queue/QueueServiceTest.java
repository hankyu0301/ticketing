package com.ticketing.domain.queue;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.application.queue.service.QueueService;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.global.common.exception.queue.QueueBadRequestException;
import com.ticketing.global.common.exception.queue.QueueConfigException;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private ConcertScheduleMapper concertScheduleMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "estimatedThroughput", 50L);
        ReflectionTestUtils.setField(queueService, "maxActiveMember", 1000L);
        ReflectionTestUtils.setField(queueService, "activeTimeMs", 300000L);

        given(concertScheduleMapper.findById(anyLong()))
                .willReturn(Optional.of(openedSchedule()));
    }

    @Test
    @DisplayName("대기열에 즉시 입장하면 entered=true를 반환한다.")
    void enterQueueFreePass() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-100L);

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("대기열에 등록되면 순번과 재시도 시간을 반환한다.")
    void enterQueueWaiting() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(4L);

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.getRank()).isEqualTo(5L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 회차에는 대기열에 진입할 수 없다.")
    void enterQueueFailScheduleNotFound() {
        given(concertScheduleMapper.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.enterQueue(999L, 1L))
                .isInstanceOf(ConcertScheduleNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
    }

    @Test
    @DisplayName("예매 오픈 전에는 대기열에 진입할 수 없다.")
    void enterQueueFailBeforeReservationOpen() {
        given(concertScheduleMapper.findById(anyLong()))
                .willReturn(Optional.of(notOpenedSchedule()));

        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueBadRequestException.class)
                .hasMessage(QueueErrorMessage.RESERVATION_NOT_OPEN);
    }

    @Test
    @DisplayName("Redis 결과가 비정상이면 대기열 진입에 실패한다.")
    void enterQueueFailError() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-1L);

        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    @Test
    @DisplayName("active 사용자의 상태를 조회하면 entered=true를 반환한다.")
    void getQueueStatusActive() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(-100L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("waiting 사용자의 상태를 조회하면 순번과 재시도 시간을 반환한다.")
    void getQueueStatusWaiting() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(49L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.isEntered()).isFalse();
        assertThat(response.getRank()).isEqualTo(50L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기 시간이 상한을 넘으면 60초로 제한한다.")
    void getQueueStatusWaitingMaxRetryTime() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(4999L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.getRetryAfter()).isEqualTo(60L);
    }

    @Test
    @DisplayName("존재하지 않는 회차의 상태는 조회할 수 없다.")
    void getQueueStatusFailScheduleNotFound() {
        given(concertScheduleMapper.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.getQueueStatus(999L, 1L))
                .isInstanceOf(ConcertScheduleNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
    }

    @Test
    @DisplayName("존재하지 않는 queue 상태는 조회할 수 없다.")
    void getQueueStatusFailNotFound() {
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(-1L);

        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    @Test
    @DisplayName("처리량 설정이 잘못되면 상태를 조회할 수 없다.")
    void getQueueStatusFailInvalidEstimatedThroughput() {
        ReflectionTestUtils.setField(queueService, "estimatedThroughput", 0L);
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(1L);

        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueConfigException.class)
                .hasMessage(QueueErrorMessage.INVALID_ESTIMATED_THROUGHPUT);
    }

    private ConcertSchedule openedSchedule() {
        return ConcertSchedule.create(
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().minusMinutes(1),
                100
        );
    }

    private ConcertSchedule notOpenedSchedule() {
        return ConcertSchedule.create(
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusMinutes(10),
                100
        );
    }
}
