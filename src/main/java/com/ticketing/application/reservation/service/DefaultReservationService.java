package com.ticketing.application.reservation.service;

import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.domain.seat.exception.SeatNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.ticketing.domain.seat.exception.SeatErrorMessage.NOT_EXIST_SEAT;

@Service
@RequiredArgsConstructor
public class DefaultReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResponse reserve(Long seatId, Long memberId, Long scheduleId) {

        // 1, 2
        Seat seat = seatMapper.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(NOT_EXIST_SEAT));
        seat.reserve(memberId);
        seatMapper.update(seat);

        // 3. 예약 정보 저장
        Reservation reservation = Reservation.create(
                memberId,
                seat.getSeatId(),
                scheduleId,
                LocalDateTime.now(),
                LocalDate.now().plusDays(1).atStartOfDay()
        );

        reservationMapper.save(reservation);

        return new ReservationResponse(reservation);
    }
}
