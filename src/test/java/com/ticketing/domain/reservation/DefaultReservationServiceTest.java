package com.ticketing.domain.reservation;

import com.ticketing.application.reservation.service.DefaultReservationService;
import com.ticketing.domain.reservation.exception.ReservationBadRequestException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static com.ticketing.domain.reservation.exception.ReservationErrorMessage.SEAT_NOT_BELONG_TO_SCHEDULE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultReservationServiceTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private SeatMapper seatMapper;

    @InjectMocks
    private DefaultReservationService reservationService;

    @Test
    @DisplayName("좌석과 회차가 일치하지 않으면 기본 예약 서비스는 실패한다.")
    void reserveFailWhenSeatDoesNotBelongToSchedule() {
        Long seatId = 1L;
        Long memberId = 10L;
        Long requestedScheduleId = 100L;
        Seat seat = createSeat(200L, seatId);

        given(seatMapper.findById(seatId)).willReturn(Optional.of(seat));

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
