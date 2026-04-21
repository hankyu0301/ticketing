package com.ticketing.application.reservation.service;

import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final PaymentMapper paymentMapper;

    @Transactional(readOnly = true)
    public List<Long> findExpiredPendingReservationIds(LocalDateTime now, int processLimit) {
        return reservationMapper.findExpiredPendingReservationIds(now, processLimit);
    }

    @Transactional
    public void expirePendingReservation(Long reservationId) {
        Reservation reservation = reservationMapper.findByIdForUpdate(reservationId);
        if (reservation == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.getStatus() != Reservation.ReservationStatus.PENDING || !reservation.isExpired(now)) {
            return;
        }

        Seat seat = seatMapper.findByIdWithPessimisticLock(reservation.getSeatId()).orElse(null);
        paymentMapper.findByReservationId(reservationId)
                .filter(payment -> payment.getStatus() == Payment.PaymentStatus.PENDING)
                .ifPresent(payment -> {
                    payment.fail();
                    paymentMapper.update(payment);
                });

        reservation.cancel();
        reservationMapper.update(reservation);

        if (seat != null) {
            seat.release();
            seatMapper.update(seat);
        }
    }
}
