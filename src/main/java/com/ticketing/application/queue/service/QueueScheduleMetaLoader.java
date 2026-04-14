package com.ticketing.application.queue.service;

import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.infrastructure.persistence.queue.QueueScheduleMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QueueScheduleMetaLoader {

    private final QueueRedisRepository queueRedisRepository;
    private final ConcertScheduleMapper concertScheduleMapper;

    public Optional<QueueScheduleMeta> getOrLoad(Long scheduleId) {
        Optional<QueueScheduleMeta> cachedMeta = queueRedisRepository.findScheduleMeta(scheduleId);
        if (cachedMeta.isPresent()) {
            return cachedMeta;
        }

        return concertScheduleMapper.findById(scheduleId)
                .map(schedule -> {
                    QueueScheduleMeta meta = QueueScheduleMeta.from(schedule);
                    queueRedisRepository.saveScheduleMeta(
                            meta.scheduleId(),
                            meta.reservationOpenAtMs(),
                            meta.concertDateMs()
                    );
                    return meta;
                });
    }
}
