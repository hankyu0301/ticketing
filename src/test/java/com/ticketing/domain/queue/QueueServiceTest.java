package com.ticketing.domain.queue;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.application.queue.service.QueueService;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @BeforeEach
        // 각 테스트 실행 전 값 주입
    void setUp() {
        ReflectionTestUtils.setField(queueService, "estimatedThroughput", 50L);
        ReflectionTestUtils.setField(queueService, "maxActiveMember", 1000L);
        ReflectionTestUtils.setField(queueService, "activeTimeMs", 300000L);
    }

    // =====================================
    // 1. 대기열 진입 (enterQueue)
    // =====================================
    @Test
    @DisplayName("[SUCCESS] 대기열 진입 - 즉시 입장 (Free Pass)")
    void enterQueue_freePass() {
        // given
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-100L);

        // when
        QueueResponse response = queueService.enterQueue(1L, 1L);

        // then
        assertThat(response.isEntered()).isEqualTo(true); // 0 반환
    }

    @Test
    @DisplayName("[SUCCESS] 대기열 진입 - 대기열 등록됨")
    void enterQueue_waiting() {
        // given
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(4L);

        // when
        QueueResponse response = queueService.enterQueue(1L, 1L);

        // then
        assertThat(response.getRank()).isEqualTo(5L);
    }

    @Test
    @DisplayName("[FAIL] 대기열 진입 실패 - 오류 발생")
    void enterQueue_fail_error() {
        // given
        // Lua Script가 -1 반환 (오류)
        given(queueRedisRepository.register(anyLong(), anyLong(), anyLong(), anyLong()))
                .willReturn(-1L);

        // when & then
        assertThatThrownBy(() -> queueService.enterQueue(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessage(QueueErrorMessage.NOT_EXIST_QUEUE);
    }

    // =====================================
    // 2. 상태 조회 (getQueueStatus)
    // =====================================

    @Test
    @DisplayName("[SUCCESS] 상태 조회 - 입장 완료 상태")
    void getQueueStatus_active() {
        // given
        given(queueRedisRepository.getRank(anyLong(), anyLong()))
                .willReturn(-100L);

        // when
        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        // then
        assertThat(response.isEntered()).isTrue();
        assertThat(response.getRank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[SUCCESS] 상태 조회 - 대기중 (재시도 시간 계산)")
    void getQueueStatus_waiting_calcRetryTime() {
        // given
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(49L);

        // when
        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        // then
        assertThat(response.isEntered()).isFalse();
        assertThat(response.getRank()).isEqualTo(50L);
        assertThat(response.getRetryAfter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[SUCCESS] 상태 조회 - 대기중 (최대 대기 시간 제한)")
    void getQueueStatus_waiting_maxRetryTime() {
        // given
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(4999L);

        // when
        QueueResponse response = queueService.getQueueStatus(1L, 1L);

        // then
        assertThat(response.getRetryAfter()).isEqualTo(60L);
    }

    @Test
    @DisplayName("[FAIL] 상태 조회 실패 - 대기열/활성열 없음")
    void getQueueStatus_fail_notFound() {
        // given
        given(queueRedisRepository.getRank(anyLong(), anyLong())).willReturn(-1L);

        // when & then
        assertThatThrownBy(() -> queueService.getQueueStatus(1L, 1L))
                .isInstanceOf(QueueNotFoundException.class);
    }
}