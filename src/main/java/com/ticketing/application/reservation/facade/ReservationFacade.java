package com.ticketing.application.reservation.facade;

import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;

public interface ReservationFacade {

    ReservationResponse reserve(Long memberId,ReservationCreateRequest req);
}
