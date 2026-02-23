package com.ticketing.domain.seat;

import com.ticketing.api.seat.dto.SeatCreateRequest;
import com.ticketing.api.seat.dto.SeatResponse;
import com.ticketing.infrastructure.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.application.seat.service.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    SeatService seatService;

    @Mock
    SeatMapper seatMapper;

    @Test
    @DisplayName("[SUCCESS] 좌석 생성 성공")
    void createSeats_success() {
        // given
        Long scheduleId = 1L;
        SeatCreateRequest request = new SeatCreateRequest();
        ReflectionTestUtils.setField(request, "totalSeats", 50);
        ReflectionTestUtils.setField(request, "grade", "VIP");
        ReflectionTestUtils.setField(request, "price", BigDecimal.valueOf(10000));

        // when
        seatService.createSeats(scheduleId, request);

        // then
        // verify: insertSeatsInBatch가 호출되었는지, 그리고 리스트 사이즈가 50인지 검증
        verify(seatMapper).insertSeatsInBatch(argThat(seats -> {
            return seats.size() == 50
                    && seats.get(0).getSeatNo() == 1
                    && seats.get(49).getSeatNo() == 50
                    && seats.get(0).getGrade().equals("VIP");
        }));
    }

    @Test
    @DisplayName("[SUCCESS] 좌석 조회 성공")
    void getSeats_success() {
        // given
        Long scheduleId = 1L;
        Seat seat1 = Seat.create(scheduleId, 1, "S", BigDecimal.valueOf(5000));
        Seat seat2 = Seat.create(scheduleId, 2, "S", BigDecimal.valueOf(5000));

        given(seatMapper.findAllByScheduleId(scheduleId))
                .willReturn(List.of(seat1, seat2));

        // when
        List<SeatResponse> result = seatService.getSeats(scheduleId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSeatNo()).isEqualTo(1);
        assertThat(result.get(1).getSeatNo()).isEqualTo(2);
    }
}