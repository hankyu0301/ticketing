package com.ticketing.application.payment.service;

import com.ticketing.application.seat.event.SeatUnavailableCacheMarkSoldOutEvent;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsyncPaymentProcessorTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AsyncPaymentProcessor asyncPaymentProcessor;

    @Test
    @DisplayName("비동기 결제 완료가 가능하면 결제, 예약, 좌석을 확정하고 좌석 캐시 갱신 이벤트를 발행한다")
    void complete_success_publishesSeatUnavailableCacheEvent() {
        Long paymentId = 100L;
        Long reservationId = 10L;
        Long memberId = 1L;
        Long scheduleId = 2L;
        Long seatId = 20L;

        Payment payment = Payment.create(reservationId, memberId, BigDecimal.valueOf(15000));
        Reservation reservation = Reservation.create(
                memberId,
                seatId,
                scheduleId,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(reservation, "reservationId", reservationId);
        Seat seat = Seat.create(scheduleId, 1, "R", BigDecimal.valueOf(15000));
        ReflectionTestUtils.setField(seat, "seatId", seatId);
        seat.reserve(memberId);

        given(paymentMapper.findById(paymentId)).willReturn(Optional.of(payment));
        given(reservationMapper.findByIdForUpdate(reservationId)).willReturn(reservation);
        given(paymentMapper.findByIdForUpdate(paymentId)).willReturn(Optional.of(payment));
        given(seatMapper.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));

        Payment result = asyncPaymentProcessor.complete(paymentId);

        InOrder inOrder = inOrder(paymentMapper, reservationMapper, seatMapper);
        inOrder.verify(paymentMapper).findById(paymentId);
        inOrder.verify(reservationMapper).findByIdForUpdate(reservationId);
        inOrder.verify(paymentMapper).findByIdForUpdate(paymentId);
        inOrder.verify(seatMapper).findByIdForUpdate(seatId);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        ArgumentCaptor<Seat> seatCaptor = ArgumentCaptor.forClass(Seat.class);

        verify(paymentMapper).update(paymentCaptor.capture());
        verify(reservationMapper).update(reservationCaptor.capture());
        verify(seatMapper).update(seatCaptor.capture());
        verify(eventPublisher).publishEvent(new SeatUnavailableCacheMarkSoldOutEvent(scheduleId, seatId));

        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(seatCaptor.getValue().getStatus()).isEqualTo(Seat.SeatStatus.SOLD_OUT);
    }
}
