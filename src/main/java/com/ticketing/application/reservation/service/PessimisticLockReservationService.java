package com.ticketing.application.reservation.service;

import com.ticketing.application.reservation.policy.ReservationExpirationPolicy;
import com.ticketing.domain.reservation.exception.ReservationBadRequestException;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.exception.SeatNotFoundException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.SEAT_NOT_BELONG_TO_SCHEDULE;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.NOT_EXIST_SEAT;

@Service
@RequiredArgsConstructor
public class PessimisticLockReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final ReservationExpirationPolicy reservationExpirationPolicy;

    @Transactional
    public Reservation reserve(Long seatId, Long memberId, Long scheduleId) {
        Seat seat = seatMapper.findByIdWithPessimisticLock(seatId)
                .orElseThrow(() -> new SeatNotFoundException(NOT_EXIST_SEAT));
        validateSeatSchedule(seat, scheduleId);
        seat.reserve(memberId);
        seatMapper.update(seat);

        LocalDateTime reservedAt = LocalDateTime.now();
        Reservation reservation = Reservation.create(
                memberId,
                seat.getSeatId(),
                scheduleId,
                reservedAt,
                reservationExpirationPolicy.calculateExpiredAt(reservedAt)
        );

        reservationMapper.save(reservation);
        return reservation;
    }

    private void validateSeatSchedule(Seat seat, Long scheduleId) {
        if (!Objects.equals(seat.getScheduleId(), scheduleId)) {
            throw new ReservationBadRequestException(SEAT_NOT_BELONG_TO_SCHEDULE);
        }
    }
}
