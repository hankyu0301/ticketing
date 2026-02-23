package com.ticketing.application.reservation.facade;

import com.ticketing.infrastructure.lock.executor.LockExecutor;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.service.DefaultReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${ticketing.lock.strategy}' != 'PESSIMISTIC'")
public class DistributedLockReservationFacade implements ReservationFacade{

    private final LockExecutor lockExecutor;
    private final DefaultReservationService reservationService;

    @Override
    public ReservationResponse reserve(Long memberId, ReservationCreateRequest req) {
        String lockName = "seat:" + req.getSeatId();
        return lockExecutor.execute(
                lockName,
                () -> reservationService.reserve(req.getSeatId(), memberId, req.getScheduleId())
        );
    }
}