package com.ticketing.application.queue.service;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.global.common.exception.queue.QueueBadRequestException;
import com.ticketing.global.common.exception.queue.QueueConfigException;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;
    private final ConcertScheduleMapper concertScheduleMapper;

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
        validateSchedulable(scheduleId);
        queueRedisRepository.addProcessingSchedule(scheduleId);

        Long result = queueRedisRepository.register(
                scheduleId,
                memberId,
                maxActiveMember,
                activeTimeMs
        );

        if (result == -100) {
            log.info("Immediate entry - User: {}, Schedule: {}", memberId, scheduleId);
            return new QueueResponse(0L, 0L, true);
        }

        if (result == -1) {
            log.error("Queue entry failed - User: {}, Schedule: {}", memberId, scheduleId);
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        long rank = result + 1;
        long retryAfter = calculateRetryTime(rank);

        log.info("Queue entry - User: {}, Schedule: {}, Rank: {}, WaitTime: {}s",
                memberId, scheduleId, rank, retryAfter);

        return new QueueResponse(rank, retryAfter, false);
    }

    public QueueResponse getQueueStatus(Long scheduleId, Long memberId) {
        validateSchedulable(scheduleId);

        Long result = queueRedisRepository.getRank(scheduleId, memberId);

        if (result == -100) {
            return new QueueResponse(0L, 0L, true);
        }

        if (result == -1) {
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        long rank = result + 1;
        long retryAfter = calculateRetryTime(rank);

        return new QueueResponse(rank, retryAfter, false);
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

    private void validateSchedulable(Long scheduleId) {
        ConcertSchedule schedule = concertScheduleMapper.findById(scheduleId)
                .orElseThrow(() -> new ConcertScheduleNotFoundException(ConcertErrorMessage.NOT_EXIST_SCHEDULE));

        if (schedule.getReservationOpenAt().isAfter(LocalDateTime.now())) {
            throw new QueueBadRequestException(QueueErrorMessage.RESERVATION_NOT_OPEN);
        }
    }
}
