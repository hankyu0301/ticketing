package com.ticketing.application.reservation.facade;

import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.service.DefaultReservationService;
import com.ticketing.domain.reservation.exception.ReservationTooManyRequestsException;
import com.ticketing.domain.seat.exception.SeatAlreadyReservedException;
import com.ticketing.infrastructure.lock.exception.LockAcquisitionFailureException;
import com.ticketing.infrastructure.lock.executor.LockExecutor;
import com.ticketing.infrastructure.persistence.seat.SeatUnavailableCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.RESERVATION_TOO_BUSY;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.ALREADY_RESERVED;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${ticketing.lock.strategy}' != 'PESSIMISTIC'")
public class DistributedLockReservationFacade implements ReservationFacade{

    private final LockExecutor lockExecutor;
    private final DefaultReservationService reservationService;
    private final SeatUnavailableCacheRepository seatUnavailableCacheRepository;

    @Value("${ticketing.reservation.unavailable-cache.enabled:true}")
    private boolean unavailableCacheEnabled;

    @Value("${ticketing.reservation.hold-duration:PT30M}")
    private Duration holdDuration;

    @Value("${ticketing.reservation.unavailable-cache.temporary-ttl-buffer:PT1M}")
    private Duration temporaryTtlBuffer;

    @Override
    public ReservationResponse reserve(Long memberId, ReservationCreateRequest req) {
        String lockName = "seat:" + req.getSeatId();
        if (unavailableCacheEnabled && seatUnavailableCacheRepository.isUnavailable(req.getScheduleId(), req.getSeatId())) {
            throw new SeatAlreadyReservedException(ALREADY_RESERVED);
        }

        try {
            ReservationResponse response = lockExecutor.execute(
                    lockName,
                    () -> reservationService.reserve(req.getSeatId(), memberId, req.getScheduleId())
            );
            markTemporaryUnavailable(req);
            return response;
        } catch (LockAcquisitionFailureException e) {
            throw new ReservationTooManyRequestsException(RESERVATION_TOO_BUSY);
        }
    }

    private void markTemporaryUnavailable(ReservationCreateRequest req) {
        if (!unavailableCacheEnabled) {
            return;
        }

        seatUnavailableCacheRepository.markTemporary(
                req.getScheduleId(),
                req.getSeatId(),
                holdDuration.plus(temporaryTtlBuffer)
        );
    }
}
