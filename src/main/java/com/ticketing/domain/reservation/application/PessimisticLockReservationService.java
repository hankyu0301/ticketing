package com.ticketing.domain.reservation.application;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.reservation.service.DefaultReservationService;
import com.ticketing.domain.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PessimisticLockReservationService implements ReservationFacade {

    private final SeatMapper seatMapper;
    private final DefaultReservationService reservationService;

    @Override
    @Transactional
    public ReservationResponse reserve(ReservationCreateRequest req) {
        Seat seat = seatMapper.findByIdWithPessimisticLock(req.getSeatId())
                .orElseThrow();
        Reservation reservation = reservationService.reserve(seat, req.getMemberId(), req.getSeatId());
        return new ReservationResponse(reservation);
    }
}
