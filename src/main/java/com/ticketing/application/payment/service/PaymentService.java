package com.ticketing.application.payment.service;

import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.domain.payment.exception.PaymentBadRequestException;
import com.ticketing.domain.payment.exception.PaymentNotFoundException;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.exception.SeatNotFoundException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static com.ticketing.domain.payment.exception.PaymentErrorMessage.NOT_EXIST_PAYMENT;
import static com.ticketing.domain.payment.exception.PaymentErrorMessage.NOT_EXIST_RESERVATION;
import static com.ticketing.domain.payment.exception.PaymentErrorMessage.PAYMENT_ALREADY_COMPLETED;
import static com.ticketing.domain.payment.exception.PaymentErrorMessage.PAYMENT_ALREADY_PROCESSING;
import static com.ticketing.domain.payment.exception.PaymentErrorMessage.PAYMENT_NOT_ALLOWED;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.NOT_EXIST_SEAT;
import static java.time.LocalDateTime.now;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentProcessor paymentProcessor;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentResponse requestSync(Long memberId, Long reservationId) {
        Payment payment = preparePayment(memberId, reservationId);
        Payment completed = paymentProcessor.complete(payment.getPaymentId());
        return new PaymentResponse(completed);
    }

    @Transactional
    public PaymentResponse requestAsync(Long memberId, Long reservationId) {
        Payment payment = preparePayment(memberId, reservationId);
        eventPublisher.publishEvent(new PaymentRequestedEvent(payment.getPaymentId(), reservationId, memberId));
        return new PaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long memberId, Long paymentId) {
        Payment payment = paymentMapper.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(NOT_EXIST_PAYMENT));

        if (!Objects.equals(payment.getMemberId(), memberId)) {
            throw new PaymentNotFoundException(NOT_EXIST_PAYMENT);
        }

        return new PaymentResponse(payment);
    }

    private Payment preparePayment(Long memberId, Long reservationId) {
        Reservation reservation = reservationMapper.findByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new PaymentNotFoundException(NOT_EXIST_RESERVATION);
        }

        Seat seat = seatMapper.findById(reservation.getSeatId())
                .orElseThrow(() -> new SeatNotFoundException(NOT_EXIST_SEAT));

        validatePayable(reservation, seat, memberId);

        return paymentMapper.findByReservationId(reservationId)
                .map(existing -> reuseOrReject(existing))
                .orElseGet(() -> createPendingPayment(reservation, memberId, seat));
    }

    private void validatePayable(Reservation reservation, Seat seat, Long memberId) {
        if (!reservation.canBePaidBy(memberId, now()) || !seat.isTemporarilyHeldBy(memberId)) {
            throw new PaymentBadRequestException(PAYMENT_NOT_ALLOWED);
        }
    }

    private Payment reuseOrReject(Payment existingPayment) {
        return switch (existingPayment.getStatus()) {
            case FAILED -> {
                existingPayment.resetPending();
                paymentMapper.update(existingPayment);
                yield existingPayment;
            }
            case PENDING -> throw new PaymentBadRequestException(PAYMENT_ALREADY_PROCESSING);
            case COMPLETED -> throw new PaymentBadRequestException(PAYMENT_ALREADY_COMPLETED);
            default -> throw new PaymentBadRequestException(PAYMENT_NOT_ALLOWED);
        };
    }

    private Payment createPendingPayment(Reservation reservation, Long memberId, Seat seat) {
        Payment payment = Payment.create(reservation.getReservationId(), memberId, seat.getPrice());
        paymentMapper.save(payment);
        return payment;
    }
}
