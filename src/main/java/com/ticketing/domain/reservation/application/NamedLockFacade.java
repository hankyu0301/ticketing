package com.ticketing.domain.reservation.application;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.executor.NamedLockExecutor;
import com.ticketing.domain.reservation.service.DefaultReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "NAMED")
public class NamedLockFacade implements ReservationFacade {

    private final NamedLockExecutor namedLockExecutor;
    private final DefaultReservationService reservationService;

    public ReservationResponse reserve(Long memberId, ReservationCreateRequest request) {
        String lockName = "seat:" + request.getSeatId();
        return namedLockExecutor.execute(lockName,
                () -> reservationService.reserve(request.getSeatId(), memberId, request.getScheduleId()));
    }
}
