package com.ticketing.application.reservation.service;

import com.ticketing.domain.reservation.exception.ReservationBadRequestException;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.domain.seat.exception.SeatNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.SEAT_NOT_BELONG_TO_SCHEDULE;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.NOT_EXIST_SEAT;

@Service
@RequiredArgsConstructor
public class PessimisticLockReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;

    /**
     * 비관적 락 예약 생성 로직
     * 1. 좌석 조회 (비관적락)
     * 2. 예약 가능 여부 체크
     * 3. 좌석 상태 변경 (AVAILABLE -> RESERVED)
     * 4. 예약 정보 저장
     */
    @Transactional
    public Reservation reserve(Long seatId, Long memberId, Long scheduleId) {

        // 1, 2
        Seat seat = seatMapper.findByIdWithPessimisticLock(seatId)
                .orElseThrow(() -> new SeatNotFoundException(NOT_EXIST_SEAT));
        validateSeatSchedule(seat, scheduleId);
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

    private void validateSeatSchedule(Seat seat, Long scheduleId) {
        if (!Objects.equals(seat.getScheduleId(), scheduleId)) {
            throw new ReservationBadRequestException(SEAT_NOT_BELONG_TO_SCHEDULE);
        }
    }
}
