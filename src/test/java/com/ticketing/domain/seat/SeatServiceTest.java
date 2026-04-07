package com.ticketing.domain.seat;

import com.ticketing.api.seat.dto.SeatCreateRequest;
import com.ticketing.api.seat.dto.SeatResponse;
import com.ticketing.application.seat.service.SeatService;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.domain.seat.exception.SeatBadRequestException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.ticketing.domain.seat.exception.SeatErrorMessage.SEAT_COUNT_MISMATCH;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.SEATS_ALREADY_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    SeatService seatService;

    @Mock
    SeatMapper seatMapper;

    @Mock
    ConcertScheduleMapper concertScheduleMapper;

    @Test
    @DisplayName("[SUCCESS] 좌석 생성 성공")
    void createSeats_success() {
        Long scheduleId = 1L;
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        given(concertScheduleMapper.findById(scheduleId)).willReturn(Optional.of(createSchedule(scheduleId, 50)));
        given(seatMapper.countByScheduleId(scheduleId)).willReturn(0L);

        seatService.createSeats(scheduleId, request);

        verify(seatMapper).insertSeatsInBatch(argThat(seats ->
                seats.size() == 50
                        && seats.get(0).getSeatNo() == 1
                        && seats.get(49).getSeatNo() == 50
                        && seats.get(0).getGrade().equals("VIP")
        ));
    }

    @Test
    @DisplayName("[FAIL] 존재하지 않는 회차에는 좌석을 생성할 수 없다")
    void createSeats_fail_scheduleNotFound() {
        Long scheduleId = 999L;
        SeatCreateRequest request = createRequest(10, "VIP", BigDecimal.valueOf(10000));

        given(concertScheduleMapper.findById(scheduleId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.createSeats(scheduleId, request))
                .isInstanceOf(ConcertScheduleNotFoundException.class);

        verify(seatMapper, never()).insertSeatsInBatch(anyList());
    }

    @Test
    @DisplayName("[FAIL] 회차의 총 좌석 수와 요청 좌석 수가 다르면 생성할 수 없다")
    void createSeats_fail_totalSeatMismatch() {
        Long scheduleId = 1L;
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        given(concertScheduleMapper.findById(scheduleId)).willReturn(Optional.of(createSchedule(scheduleId, 30)));

        assertThatThrownBy(() -> seatService.createSeats(scheduleId, request))
                .isInstanceOf(SeatBadRequestException.class)
                .hasMessage(SEAT_COUNT_MISMATCH);

        verify(seatMapper, never()).insertSeatsInBatch(anyList());
    }

    @Test
    @DisplayName("[FAIL] 이미 좌석이 생성된 회차에는 다시 생성할 수 없다")
    void createSeats_fail_alreadyCreated() {
        Long scheduleId = 1L;
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        given(concertScheduleMapper.findById(scheduleId)).willReturn(Optional.of(createSchedule(scheduleId, 50)));
        given(seatMapper.countByScheduleId(scheduleId)).willReturn(1L);

        assertThatThrownBy(() -> seatService.createSeats(scheduleId, request))
                .isInstanceOf(SeatBadRequestException.class)
                .hasMessage(SEATS_ALREADY_CREATED);

        verify(seatMapper, never()).insertSeatsInBatch(anyList());
    }

    @Test
    @DisplayName("[SUCCESS] 좌석 조회 성공")
    void getSeats_success() {
        Long scheduleId = 1L;
        Seat seat1 = Seat.create(scheduleId, 1, "S", BigDecimal.valueOf(5000));
        Seat seat2 = Seat.create(scheduleId, 2, "S", BigDecimal.valueOf(5000));

        given(seatMapper.findAllByScheduleId(scheduleId))
                .willReturn(List.of(seat1, seat2));

        List<SeatResponse> result = seatService.getSeats(scheduleId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSeatNo()).isEqualTo(1);
        assertThat(result.get(1).getSeatNo()).isEqualTo(2);
    }

    private SeatCreateRequest createRequest(int totalSeats, String grade, BigDecimal price) {
        SeatCreateRequest request = new SeatCreateRequest();
        ReflectionTestUtils.setField(request, "totalSeats", totalSeats);
        ReflectionTestUtils.setField(request, "grade", grade);
        ReflectionTestUtils.setField(request, "price", price);
        return request;
    }

    private ConcertSchedule createSchedule(Long scheduleId, int totalSeats) {
        ConcertSchedule schedule = ConcertSchedule.create(
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now(),
                totalSeats
        );
        ReflectionTestUtils.setField(schedule, "scheduleId", scheduleId);
        return schedule;
    }
}
