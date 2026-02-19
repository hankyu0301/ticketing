package com.ticketing.domain.reservation.application;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;

public interface ReservationFacade {

    ReservationResponse reserve(Long memberId,ReservationCreateRequest req);
}
