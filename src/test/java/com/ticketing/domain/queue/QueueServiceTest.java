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
    QueueService queueService;

    @Mock
    QueueRedisRepository queueRedisRepository;

    @Mock
    ConcertScheduleMapper concertScheduleMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "estimatedThroughput", 50L);
        ReflectionTestUtils.setField(queueService, "maxActiveMember", 1000L);
        ReflectionTestUtils.setField(queueService, "activeTimeMs", 300000L);

        given(concertScheduleMapper.findById(anyLong()))
                .willReturn(Optional.of(openedSchedule()));
    }

    @Test
    @DisplayName("[SUCCESS] 대기열 진입 - 즉시 입장")
    void enterQueue_freePass() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-100L);

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[SUCCESS] 대기열 진입 - 대기열 등록")
    void enterQueue_waiting() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(4L);

        QueueResponse response = queueService.enterQueue(1L, 1L);

        assertThat(response.getRank()).isEqualTo(5L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[FAIL] 대기열 진입 실패 - 존재하지 않는 회차")
    void enterQueue_fail_scheduleNotFound() {
        given(concertScheduleMapper.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.enterQueue(999L, 1L))
                .isInstanceOf(ConcertScheduleNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
    }

    @Test
    @DisplayName("[FAIL] 대기열 진입 실패 - 예매 오픈 전")
    void enterQueue_fail_beforeReservationOpen() {
        given(concertScheduleMapper.findById(anyLong()))
                .willReturn(Optional.of(notOpenedSchedule()));

        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueBadRequestException.class)
                .hasMessage(QueueErrorMessage.RESERVATION_NOT_OPEN);
    }

    @Test
    @DisplayName("[FAIL] 대기열 진입 실패 - Redis 결과 오류")
    void enterQueue_fail_error() {
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-1L);

        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    @Test
    @DisplayName("[SUCCESS] 상태 조회 - active 사용자")
    void getQueueStatus_active() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(-100L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[SUCCESS] 상태 조회 - waiting 사용자")
    void getQueueStatus_waiting() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(49L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.isEntered()).isFalse();
        assertThat(response.getRank()).isEqualTo(50L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[SUCCESS] 상태 조회 - 대기 시간 상한 적용")
    void getQueueStatus_waiting_maxRetryTime() {
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(4999L);

        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        assertThat(response.getRetryAfter()).isEqualTo(60L);
    }

    @Test
    @DisplayName("[FAIL] 상태 조회 실패 - 존재하지 않는 회차")
    void getQueueStatus_fail_scheduleNotFound() {
        given(concertScheduleMapper.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.getQueueStatus(999L, 1L))
                .isInstanceOf(ConcertScheduleNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
    }

    @Test
    @DisplayName("[FAIL] 상태 조회 실패 - queue 미존재")
    void getQueueStatus_fail_notFound() {
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(-1L);

        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    @Test
    @DisplayName("[FAIL] 상태 조회 실패 - 잘못된 처리량 설정")
    void getQueueStatus_fail_invalidEstimatedThroughput() {
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
