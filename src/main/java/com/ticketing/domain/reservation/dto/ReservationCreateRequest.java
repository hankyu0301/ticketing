package com.ticketing.domain.reservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.ticketing.global.common.exception.reservation.ReservationErrorMessage.*;

@Getter
@NoArgsConstructor
public class ReservationCreateRequest {

    @NotNull(message = SCHEDULE_ID_NOT_EMPTY)
    private Long scheduleId;

    @NotNull(message = SEAT_ID_MUST_NOT_EMPTY)
    private Long seatId;

}