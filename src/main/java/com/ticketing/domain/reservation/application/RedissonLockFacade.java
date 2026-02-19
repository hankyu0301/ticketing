package com.ticketing.domain.reservation.application;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.executor.RedissonLockExecutor;
import com.ticketing.domain.reservation.service.DefaultReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "REDISSON")
public class RedissonLockFacade implements ReservationFacade {

    private final RedissonLockExecutor redissonLockExecutor;
    private final DefaultReservationService reservationService;

    @Override
    public ReservationResponse reserve(Long memberId, ReservationCreateRequest req) {
        String lockName = "seat:" + req.getSeatId();

        return redissonLockExecutor.execute(
                lockName,
                () -> reservationService.reserve(req.getSeatId(), memberId, req.getScheduleId())
        );
    }
}