package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.application.ReservationFacade;
import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {

    private final ReservationFacade reservationFacade;

    @Autowired
    public ReservationService(@Qualifier("pessimisticLockReservationService") ReservationFacade reservationFacade) {
        this.reservationFacade = reservationFacade;
    }

    public ReservationResponse reserve(ReservationCreateRequest req) {
        return reservationFacade.reserve(req);
    }
}
