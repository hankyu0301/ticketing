package com.ticketing.domain.concert.dto;

import com.ticketing.domain.concert.model.ConcertSchedule;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ConcertScheduleDto {

    private Long scheduleId;
    private Long concertId;
    private LocalDateTime concertDate;
    private LocalDateTime reservationOpenAt;
    private Integer totalSeats;

    public ConcertScheduleDto(ConcertSchedule schedule) {
        this.scheduleId = schedule.getScheduleId();
        this.concertId = schedule.getConcertId();
        this.concertDate = schedule.getConcertDate();
        this.reservationOpenAt = schedule.getReservationOpenAt();
        this.totalSeats = schedule.getTotalSeats();
    }
}
