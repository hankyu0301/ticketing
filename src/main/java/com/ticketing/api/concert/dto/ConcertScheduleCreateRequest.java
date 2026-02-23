package com.ticketing.api.concert.dto;

import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ConcertScheduleCreateRequest {

    @NotNull(message = ConcertErrorMessage.CONCERT_DATE_MUST_NOT_EMPTY)
    @Future(message = ConcertErrorMessage.CONCERT_DATE_MUST_BE_FUTURE)
    private LocalDateTime concertDate;

    @NotNull(message = ConcertErrorMessage.RESERVATION_OPEN_AT_MUST_NOT_EMPTY)
    private LocalDateTime reservationOpenAt;

    @NotNull(message = ConcertErrorMessage.TOTAL_SCORE_MUST_NOT_EMPTY)
    @Min(value = 1, message = ConcertErrorMessage.TOTAL_SCORE_MUST_BE_POSITIVE)
    private Integer totalSeats;

    public ConcertSchedule toEntity(Long concertId) {
        return ConcertSchedule.create(concertId, this.concertDate, this.reservationOpenAt, this.totalSeats);
    }
}