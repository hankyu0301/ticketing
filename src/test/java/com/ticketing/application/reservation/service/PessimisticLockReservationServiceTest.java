package com.ticketing.application.reservation.service;

import com.ticketing.application.reservation.policy.ReservationExpirationPolicy;
import com.ticketing.domain.reservation.exception.ReservationBadRequestException;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.SEAT_NOT_BELONG_TO_SCHEDULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PessimisticLockReservationServiceTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private ReservationExpirationPolicy reservationExpirationPolicy;

    @InjectMocks
    private PessimisticLockReservationService reservationService;

    @Test
    @DisplayName("예약 생성 시 만료 시각은 정책으로 계산한다")
    void reserveStoresExpirationBasedOnPolicy() {
        Long seatId = 1L;
        Long memberId = 10L;
        Long scheduleId = 100L;
        Seat seat = createSeat(scheduleId, seatId);

        given(reservationExpirationPolicy.calculateExpiredAt(any(LocalDateTime.class)))
                .willAnswer(invocation -> invocation.getArgument(0, LocalDateTime.class).plusMinutes(30));
        given(seatMapper.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));

        reservationService.reserve(seatId, memberId, scheduleId);

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationMapper).save(reservationCaptor.capture());

        Reservation savedReservation = reservationCaptor.getValue();
        assertThat(savedReservation.getReservedAt()).isNotNull();
        assertThat(savedReservation.getExpiredAt()).isEqualTo(savedReservation.getReservedAt().plusMinutes(30));
        verify(reservationExpirationPolicy).calculateExpiredAt(savedReservation.getReservedAt());
        verify(seatMapper).update(seat);
    }

    @Test
    @DisplayName("좌석과 회차가 일치하지 않으면 비관적 락 예약 서비스는 실패한다.")
    void reserveFailWhenSeatDoesNotBelongToSchedule() {
        Long seatId = 1L;
        Long memberId = 10L;
        Long requestedScheduleId = 100L;
        Seat seat = createSeat(200L, seatId);

        given(seatMapper.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> reservationService.reserve(seatId, memberId, requestedScheduleId))
                .isInstanceOf(ReservationBadRequestException.class)
                .hasMessage(SEAT_NOT_BELONG_TO_SCHEDULE);

        verify(seatMapper, never()).update(any());
        verify(reservationMapper, never()).save(any());
    }

    private Seat createSeat(Long scheduleId, Long seatId) {
        Seat seat = Seat.create(scheduleId, 1, "VIP", BigDecimal.valueOf(10000));
        ReflectionTestUtils.setField(seat, "seatId", seatId);
        return seat;
    }
}
