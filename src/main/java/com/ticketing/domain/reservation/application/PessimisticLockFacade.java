package com.ticketing.domain.reservation.application;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.reservation.service.PessimisticLockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "PESSIMISTIC")
public class PessimisticLockFacade implements ReservationFacade {

    private final PessimisticLockReservationService reservationService;

    @Override
    @Transactional
    public ReservationResponse reserve(Long memberId, ReservationCreateRequest req) {
        Reservation reservation = reservationService.reserve(req.getSeatId(), memberId, req.getSeatId());
        return new ReservationResponse(reservation);
    }
}
