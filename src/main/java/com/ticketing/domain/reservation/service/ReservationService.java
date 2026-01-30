package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.application.ReservationFacade;
import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.mapper.ReservationMapper;
import com.ticketing.domain.reservation.model.Reservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(Long memberId) {
        List<Reservation> reservations = reservationMapper.findAllByMemberId(memberId);

        return reservations.stream()
                .map(ReservationResponse::new)
                .collect(Collectors.toList());
    }
}
