package com.ticketing.domain.reservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReservationCreateRequest {

    @NotNull
    private Long memberId;

    @NotNull
    private Long scheduleId;

    @NotNull
    private Long seatId;

}