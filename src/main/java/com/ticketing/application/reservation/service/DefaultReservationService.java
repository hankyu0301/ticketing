package com.ticketing.application.reservation.service;

import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.policy.ReservationExpirationPolicy;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.domain.reservation.exception.ReservationBadRequestException;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.exception.SeatAlreadyReservedException;
import com.ticketing.domain.seat.exception.SeatNotFoundException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.SEAT_NOT_BELONG_TO_SCHEDULE;
import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.RESERVATION_CLOSED;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.ALREADY_RESERVED;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.NOT_EXIST_SEAT;

@Service
@RequiredArgsConstructor
public class DefaultReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final ConcertScheduleMapper concertScheduleMapper;
    private final ReservationExpirationPolicy reservationExpirationPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResponse reserve(Long seatId, Long memberId, Long scheduleId) {
        Seat seat = seatMapper.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(NOT_EXIST_SEAT));
        validateSeatSchedule(seat, scheduleId);
        validateReservationOpen(scheduleId);
        seat.reserve(memberId);
        int updatedRows = seatMapper.reserveIfAvailable(seatId, scheduleId, memberId);
        if (updatedRows != 1) {
            throw new SeatAlreadyReservedException(ALREADY_RESERVED);
        }

        LocalDateTime reservedAt = LocalDateTime.now();
        Reservation reservation = Reservation.create(
                memberId,
                seat.getSeatId(),
                scheduleId,
                reservedAt,
                reservationExpirationPolicy.calculateExpiredAt(reservedAt)
        );

        reservationMapper.save(reservation);
        return new ReservationResponse(reservation);
    }

    private void validateSeatSchedule(Seat seat, Long scheduleId) {
        if (!Objects.equals(seat.getScheduleId(), scheduleId)) {
            throw new ReservationBadRequestException(SEAT_NOT_BELONG_TO_SCHEDULE);
        }
    }

    private void validateReservationOpen(Long scheduleId) {
        ConcertSchedule schedule = concertScheduleMapper.findById(scheduleId)
                .orElseThrow(() -> new ConcertScheduleNotFoundException(ConcertErrorMessage.NOT_EXIST_SCHEDULE));

        if (!schedule.getConcertDate().isAfter(LocalDateTime.now())) {
            throw new ReservationBadRequestException(RESERVATION_CLOSED);
        }
    }
}
