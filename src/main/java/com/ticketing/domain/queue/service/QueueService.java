package com.ticketing.domain.queue.service;

import com.ticketing.domain.queue.dto.QueueResponse;
import com.ticketing.domain.queue.repository.QueueRedisRepository;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;

    @Value("${queue.estimated-throughput:50}")
    private long estimatedThroughput;

    @Value("${queue.max-active-member:1000}")
    private long maxActiveMember;

    @Value("${queue.active-time-ms:300000}") // 5분 = 300000ms
    private long activeTimeMs;

    /**
     * 대기열 진입
     */
    public QueueResponse enterQueue(Long scheduleId, Long memberId) {
        // 1. 스케줄 관리 등록 (가장 먼저 수행)
        queueRedisRepository.addProcessingSchedule(scheduleId);

        // 2. 대기열 진입 시도
        Long result = queueRedisRepository.register(
                scheduleId,
                memberId,
                maxActiveMember,
                activeTimeMs
        );

        // 3. 결과 처리 및 로그
        if (result == -100) {
            log.info("즉시 입장 - User: {}, Schedule: {}", memberId, scheduleId);
            return new QueueResponse(0L, 0L, true);
        }

        if (result == -1) {
            log.error("대기열 진입 실패 - User: {}, Schedule: {}", memberId, scheduleId);
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        long rank = result + 1;
        long retryAfter = calculateRetryTime(rank);

        log.info("대기열 진입 - User: {}, Schedule: {}, Rank: {}, WaitTime: {}s",
                memberId, scheduleId, rank, retryAfter);

        return new QueueResponse(rank, retryAfter, false);
    }

    /**
     * 상태 조회 (Polling)
     */
    public QueueResponse getQueueStatus(Long scheduleId, Long memberId) {
        Long result = queueRedisRepository.getRank(scheduleId, memberId);

        if (result == -100) {
            return new QueueResponse(0L, 0L, true); // 입장 성공
        }

        if (result == -1) {
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        long rank = result + 1;
        long retryAfter = calculateRetryTime(rank);

        return new QueueResponse(rank, retryAfter, false);
    }

    private long calculateRetryTime(long rank) {
        long seconds = rank / estimatedThroughput;

        if (seconds < 1) return 1;
        if (seconds > 60) return 60;

        return seconds;
    }
}