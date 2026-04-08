package com.ticketing.application.payment.service;

import com.ticketing.domain.payment.exception.PaymentNotFoundException;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.ticketing.domain.payment.exception.PaymentErrorMessage.NOT_EXIST_PAYMENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessor {

    private final PaymentMapper paymentMapper;
    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;

    @Transactional
    public Payment complete(Long paymentId) {
        Long reservationId = paymentMapper.findReservationIdByPaymentId(paymentId);
        if (reservationId == null) {
            throw new PaymentNotFoundException(NOT_EXIST_PAYMENT);
        }

        Reservation reservation = reservationMapper.findByIdForUpdate(reservationId);
        Payment payment = paymentMapper.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(NOT_EXIST_PAYMENT));

        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            return payment;
        }

        if (reservation == null) {
            payment.fail();
            paymentMapper.update(payment);
            return payment;
        }

        Seat seat = seatMapper.findByIdWithPessimisticLock(reservation.getSeatId()).orElse(null);
        if (seat == null) {
            payment.fail();
            paymentMapper.update(payment);
            return payment;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!reservation.canBePaidBy(payment.getMemberId(), now) || !seat.isTemporarilyHeldBy(payment.getMemberId())) {
            payment.fail();
            paymentMapper.update(payment);
            return payment;
        }

        payment.complete(now);
        reservation.confirm();
        seat.sellOut(payment.getMemberId());

        paymentMapper.update(payment);
        reservationMapper.update(reservation);
        seatMapper.update(seat);

        log.info("Payment completed. paymentId={}, reservationId={}", paymentId, reservation.getReservationId());
        return payment;
    }
}
