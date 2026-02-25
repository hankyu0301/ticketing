package com.ticketing.api.reservation.dto;

import com.ticketing.domain.reservation.model.Reservation;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReservationResponse {
    private Long reservationId;
    private Long memberId;
    private Long seatId;
    private Long scheduleId;
    private String status;

    public ReservationResponse(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.memberId = reservation.getMemberId();
        this.seatId = reservation.getSeatId();
        this.scheduleId = reservation.getScheduleId();
        this.status = reservation.getStatus().toString();
    }
}
