package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.application.ReservationFacade;
import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.mapper.ReservationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
@Service
public class ReservationService {

    private final ReservationFacade reservationFacade;
    private final ReservationMapper reservationMapper;

    @Autowired
    public ReservationService(@Qualifier("pessimisticLockReservationService") ReservationFacade reservationFacade, ReservationMapper reservationMapper) {
        this.reservationFacade = reservationFacade;
        this.reservationMapper = reservationMapper;
    }

    public ReservationResponse reserve(Long memberId, ReservationCreateRequest req) {
        return reservationFacade.reserve(memberId, req);
    }
}
