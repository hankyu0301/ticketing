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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private static final long IMMEDIATE_ENTRY = -100L;
    private static final long QUEUE_NOT_FOUND = -1L;
    private static final long META_MISSING = -2L;
    private static final long RESERVATION_NOT_OPEN = -3L;

    private final QueueRedisRepository queueRedisRepository;
    private final QueueScheduleMetaLoader queueScheduleMetaLoader;

    @Value("${queue.estimated-throughput:50}")
    private long estimatedThroughput;

    @Value("${queue.max-active-member:1000}")
    private long maxActiveMember;

    @Value("${queue.active-time-ms:300000}")
    private long activeTimeMs;

    @PostConstruct
    void validateQueueConfig() {
        if (estimatedThroughput <= 0) {
            throw new QueueConfigException(QueueErrorMessage.INVALID_ESTIMATED_THROUGHPUT);
        }
        if (maxActiveMember <= 0) {
            throw new QueueConfigException(QueueErrorMessage.INVALID_MAX_ACTIVE_MEMBER);
        }
        if (activeTimeMs <= 0) {
            throw new QueueConfigException(QueueErrorMessage.INVALID_ACTIVE_TIME);
        }
    }

    public QueueResponse enterQueue(Long scheduleId, Long memberId) {
        Long result = executeWithMetaRecovery(scheduleId, () -> queueRedisRepository.register(
                scheduleId,
                memberId,
                maxActiveMember,
                activeTimeMs
        ));

        if (result == IMMEDIATE_ENTRY) {
            log.debug("Immediate entry - User: {}, Schedule: {}", memberId, scheduleId);
            return new QueueResponse(0L, 0L, true);
        }

        if (result == QUEUE_NOT_FOUND) {
            log.error("Queue entry failed - User: {}, Schedule: {}", memberId, scheduleId);
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        queueRedisRepository.refreshProcessingSchedule(scheduleId);

        long rank = result + 1;
        long retryAfter = calculateRetryTime(rank);

        log.debug("Queue entry - User: {}, Schedule: {}, Rank: {}, WaitTime: {}s",
                memberId, scheduleId, rank, retryAfter);

        return new QueueResponse(rank, retryAfter, false);
    }

    public QueueResponse getQueueStatus(Long scheduleId, Long memberId) {
        Long result = executeWithMetaRecovery(scheduleId, () -> queueRedisRepository.getRank(scheduleId, memberId));

        if (result == IMMEDIATE_ENTRY) {
            return new QueueResponse(0L, 0L, true);
        }

        if (result == QUEUE_NOT_FOUND) {
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        long rank = result + 1;
        long retryAfter = calculateRetryTime(rank);

        return new QueueResponse(rank, retryAfter, false);
    }

    public void leaveActive(Long scheduleId, Long memberId) {
        queueRedisRepository.removeActiveMember(scheduleId, memberId);
        log.debug("Active leave - User: {}, Schedule: {}", memberId, scheduleId);
    }

    private long calculateRetryTime(long rank) {
        if (estimatedThroughput <= 0) {
            throw new QueueConfigException(QueueErrorMessage.INVALID_ESTIMATED_THROUGHPUT);
        }

        long seconds = rank / estimatedThroughput;

        if (seconds < 1) {
            return 1;
        }
        if (seconds > 60) {
            return 60;
        }
        return seconds;
    }

    private Long executeWithMetaRecovery(Long scheduleId, Supplier<Long> action) {
        Long result = action.get();

        if (result != null && result == META_MISSING) {
            Optional<QueueScheduleMeta> meta = queueScheduleMetaLoader.getOrLoad(scheduleId);
            if (meta.isEmpty()) {
                throw new ConcertScheduleNotFoundException(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
            }

            result = action.get();
            if (result != null && result == META_MISSING) {
                throw new ConcertScheduleNotFoundException(ConcertErrorMessage.NOT_EXIST_SCHEDULE);
            }
        }

        if (result != null && result == RESERVATION_NOT_OPEN) {
            throw new QueueBadRequestException(QueueErrorMessage.RESERVATION_NOT_OPEN);
        }

        return result;
    }
}
