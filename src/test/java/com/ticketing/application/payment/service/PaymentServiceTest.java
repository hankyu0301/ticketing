package com.ticketing.application.payment.service;

import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.application.seat.event.SeatUnavailableCacheMarkSoldOutEvent;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("동기 결제 요청은 서비스에서 즉시 결제를 완료한다.")
    void requestSyncCompletesPaymentDirectly() {
        Long memberId = 1L;
        Long reservationId = 10L;
        Long seatId = 100L;

        Reservation reservation = reservation(memberId, reservationId, seatId);
        Seat seat = temporarySeat(seatId, memberId);

        given(reservationMapper.findByIdForUpdate(reservationId)).willReturn(reservation);
        given(seatMapper.findById(seatId)).willReturn(Optional.of(seat));
        given(paymentMapper.findByReservationId(reservationId)).willReturn(Optional.empty());

        PaymentResponse response = paymentService.requestSync(memberId, reservationId);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        ArgumentCaptor<Seat> seatCaptor = ArgumentCaptor.forClass(Seat.class);

        verify(paymentMapper).save(any(Payment.class));
        verify(paymentMapper).update(paymentCaptor.capture());
        verify(reservationMapper).update(reservationCaptor.capture());
        verify(seatMapper).update(seatCaptor.capture());
        verify(eventPublisher).publishEvent(new SeatUnavailableCacheMarkSoldOutEvent(1L, seatId));

        Payment updatedPayment = paymentCaptor.getValue();
        Reservation updatedReservation = reservationCaptor.getValue();
        Seat updatedSeat = seatCaptor.getValue();

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(updatedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(updatedPayment.getPaidAt()).isNotNull();
        assertThat(updatedReservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(updatedSeat.getStatus()).isEqualTo(Seat.SeatStatus.SOLD_OUT);
        assertThat(updatedSeat.getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("비동기 결제 요청은 결제를 준비한 뒤 이벤트만 발행한다.")
    void requestAsyncPublishesEvent() {
        Long memberId = 1L;
        Long reservationId = 10L;
        Long seatId = 100L;

        Reservation reservation = reservation(memberId, reservationId, seatId);
        Seat seat = temporarySeat(seatId, memberId);

        given(reservationMapper.findByIdForUpdate(reservationId)).willReturn(reservation);
        given(seatMapper.findById(seatId)).willReturn(Optional.of(seat));
        given(paymentMapper.findByReservationId(reservationId)).willReturn(Optional.empty());
        doAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "paymentId", 999L);
            return null;
        }).when(paymentMapper).save(any(Payment.class));

        PaymentResponse response = paymentService.requestAsync(memberId, reservationId);

        verify(eventPublisher).publishEvent(new PaymentRequestedEvent(999L, reservationId, memberId));
        verify(paymentMapper, never()).update(any(Payment.class));
        verify(reservationMapper, never()).update(any(Reservation.class));
        verify(seatMapper, never()).update(any(Seat.class));
        verify(eventPublisher, never()).publishEvent(new SeatUnavailableCacheMarkSoldOutEvent(1L, seatId));

        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    private Reservation reservation(Long memberId, Long reservationId, Long seatId) {
        Reservation reservation = Reservation.create(
                memberId,
                seatId,
                1L,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(reservation, "reservationId", reservationId);
        return reservation;
    }

    private Seat temporarySeat(Long seatId, Long memberId) {
        Seat seat = Seat.create(1L, 1, "R", BigDecimal.valueOf(15000));
        ReflectionTestUtils.setField(seat, "seatId", seatId);
        seat.reserve(memberId);
        return seat;
    }
}
