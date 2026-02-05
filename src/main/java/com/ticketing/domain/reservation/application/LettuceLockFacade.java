package com.ticketing.domain.reservation.application;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.executor.LettuceLockExecutor;
import com.ticketing.domain.reservation.service.DefaultReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "LETTUCE")
public class LettuceLockFacade implements ReservationFacade {

    private final LettuceLockExecutor lettuceLockExecutor;
    private final DefaultReservationService reservationService;

    @Override
    public ReservationResponse reserve(Long memberId, ReservationCreateRequest req) {
        return lettuceLockExecutor.execute(
                req.getSeatId(),
                () -> reservationService.reserve(req.getSeatId(), memberId, req.getScheduleId())
        );
    }
}