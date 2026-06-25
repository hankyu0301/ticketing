package com.ticketing.application.payment.service;

import com.ticketing.domain.payment.exception.PaymentNotFoundException;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.application.seat.event.SeatUnavailableCacheMarkSoldOutEvent;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.ticketing.domain.payment.exception.PaymentErrorMessage.NOT_EXIST_PAYMENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncPaymentProcessor {

    private final PaymentMapper paymentMapper;
    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment complete(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            return payment;
        }

        Reservation reservation = reservationMapper.findByIdForUpdate(payment.getReservationId());
        if (reservation == null) {
            return failPayment(payment);
        }

        Seat seat = seatMapper.findByIdForUpdate(reservation.getSeatId()).orElse(null);
        if (seat == null) {
            return failPayment(payment);
        }

        if (!isPaymentAllowed(payment, reservation, seat)) {
            return failPayment(payment);
        }

        completePayment(payment, reservation, seat);
        log.info("Payment completed. paymentId={}, reservationId={}", paymentId, reservation.getReservationId());
        return payment;
    }

    private Payment findPayment(Long paymentId) {
        return paymentMapper.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(NOT_EXIST_PAYMENT));
    }

    private boolean isPaymentAllowed(Payment payment, Reservation reservation, Seat seat) {
        LocalDateTime now = LocalDateTime.now();
        return reservation.canBePaidBy(payment.getMemberId(), now)
                && seat.isTemporarilyHeldBy(payment.getMemberId());
    }

    private Payment failPayment(Payment payment) {
        payment.fail();
        paymentMapper.update(payment);
        return payment;
    }

    private void completePayment(Payment payment, Reservation reservation, Seat seat) {
        LocalDateTime now = LocalDateTime.now();
        payment.complete(now);
        reservation.confirm();
        seat.sellOut(payment.getMemberId());

        paymentMapper.update(payment);
        reservationMapper.update(reservation);
        seatMapper.update(seat);
        eventPublisher.publishEvent(
                new SeatUnavailableCacheMarkSoldOutEvent(reservation.getScheduleId(), reservation.getSeatId())
        );
    }
}
