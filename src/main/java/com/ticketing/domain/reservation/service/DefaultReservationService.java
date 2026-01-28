package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.reservation.mapper.ReservationMapper;
import com.ticketing.domain.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DefaultReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;

    /**
     * 기본적인 예약 생성 로직
     * 1. 예약 가능 여부 체크
     * 2. 좌석 상태 변경 (AVAILABLE -> RESERVED)
     * 3. 예약 정보 저장
     */
    @Transactional
    public Reservation reserve(Seat seat, Long memberId, Long scheduleId) {

        // 1, 2
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

        return reservation;
    }
}