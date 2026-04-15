package com.ticketing.infrastructure.persistence.queue;

import com.ticketing.domain.concert.model.ConcertSchedule;

import java.time.ZoneId;

public record QueueScheduleMeta(
        Long scheduleId,
        long reservationOpenAtMs,
        long concertDateMs
) {
    public static QueueScheduleMeta from(ConcertSchedule schedule) {
        return new QueueScheduleMeta(
                schedule.getScheduleId(),
                schedule.getReservationOpenAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                schedule.getConcertDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        );
    }
}
