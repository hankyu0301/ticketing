package com.ticketing.domain.concert;

import com.ticketing.api.concert.dto.ConcertCreateRequest;
import com.ticketing.api.concert.dto.ConcertDto;
import com.ticketing.api.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.api.concert.dto.ConcertScheduleDto;
import com.ticketing.application.concert.service.ConcertService;
import com.ticketing.domain.concert.exception.ConcertBadRequestException;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertNotFoundException;
import com.ticketing.infrastructure.persistence.concert.ConcertMapper;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @InjectMocks
    ConcertService concertService;

    @Mock
    ConcertMapper concertMapper;

    @Mock
    ConcertScheduleMapper concertScheduleMapper;

    @Test
    @DisplayName("[SUCCESS] 콘서트 생성")
    void createConcert_success() {
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "title", "테스트 콘서트");
        ReflectionTestUtils.setField(request, "description", "설명");

        doAnswer(invocation -> {
            Concert argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "concertId", 1L);
            return null;
        }).when(concertMapper).insertConcert(any(Concert.class));

        Long resultId = concertService.createConcert(request);

        assertThat(resultId).isEqualTo(1L);
        verify(concertMapper, times(1)).insertConcert(any(Concert.class));
    }

    @Test
    @DisplayName("[SUCCESS] 스케줄 생성 성공")
    void createSchedule_success() {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(request, "totalSeats", 100);

        given(concertMapper.existsById(concertId)).willReturn(true);

        doAnswer(invocation -> {
            ConcertSchedule argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "scheduleId", 10L);
            return null;
        }).when(concertScheduleMapper).insertSchedule(any(ConcertSchedule.class));

        Long scheduleId = concertService.createSchedule(concertId, request);

        assertThat(scheduleId).isEqualTo(10L);
        verify(concertScheduleMapper).insertSchedule(any(ConcertSchedule.class));
    }

    @Test
    @DisplayName("[FAIL] 존재하지 않는 콘서트에는 스케줄을 생성할 수 없다")
    void createSchedule_fail_concertNotFound() {
        Long invalidConcertId = 999L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();

        given(concertMapper.existsById(invalidConcertId)).willReturn(false);

        assertThatThrownBy(() -> concertService.createSchedule(invalidConcertId, request))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_CONCERT);

        verify(concertScheduleMapper, never()).insertSchedule(any(ConcertSchedule.class));
    }

    @Test
    @DisplayName("[FAIL] 예매 오픈 시간이 과거면 스케줄을 생성할 수 없다")
    void createSchedule_fail_reservationOpenAtInPast() {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(request, "totalSeats", 100);

        given(concertMapper.existsById(concertId)).willReturn(true);

        assertThatThrownBy(() -> concertService.createSchedule(concertId, request))
                .isInstanceOf(ConcertBadRequestException.class)
                .hasMessage(ConcertErrorMessage.RESERVATION_OPEN_AT_MUST_BE_FUTURE);

        verify(concertScheduleMapper, never()).insertSchedule(any(ConcertSchedule.class));
    }

    @Test
    @DisplayName("[FAIL] 예매 오픈 시간은 공연 시작 전이어야 한다")
    void createSchedule_fail_reservationOpenAtAfterConcertDate() {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(2));
        ReflectionTestUtils.setField(request, "totalSeats", 100);

        given(concertMapper.existsById(concertId)).willReturn(true);

        assertThatThrownBy(() -> concertService.createSchedule(concertId, request))
                .isInstanceOf(ConcertBadRequestException.class)
                .hasMessage(ConcertErrorMessage.RESERVATION_OPEN_AT_MUST_BE_BEFORE_CONCERT_DATE);

        verify(concertScheduleMapper, never()).insertSchedule(any(ConcertSchedule.class));
    }

    @Test
    @DisplayName("[SUCCESS] 콘서트 조회 성공")
    void getConcert_success() {
        Long concertId = 1L;
        Concert concert = Concert.create("조회된 콘서트", "설명");
        ReflectionTestUtils.setField(concert, "concertId", concertId);

        given(concertMapper.findById(concertId)).willReturn(Optional.of(concert));

        ConcertDto result = concertService.getConcert(concertId);

        assertThat(result.getConcertId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("조회된 콘서트");
    }

    @Test
    @DisplayName("[FAIL] 존재하지 않는 콘서트는 조회할 수 없다")
    void getConcert_fail_notFound() {
        Long invalidId = 999L;
        given(concertMapper.findById(invalidId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> concertService.getConcert(invalidId))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_CONCERT);
    }

    @Test
    @DisplayName("[SUCCESS] 스케줄 목록 조회 성공")
    void getSchedules_success() {
        Long concertId = 1L;
        ConcertSchedule schedule = ConcertSchedule.create(concertId, LocalDateTime.now(), LocalDateTime.now(), 100);

        given(concertMapper.existsById(concertId)).willReturn(true);
        given(concertScheduleMapper.findAllByConcertId(concertId)).willReturn(List.of(schedule));

        List<ConcertScheduleDto> result = concertService.getSchedules(concertId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("[FAIL] 존재하지 않는 콘서트의 스케줄 목록은 조회할 수 없다")
    void getSchedules_fail_concertNotFound() {
        Long invalidId = 999L;
        given(concertMapper.existsById(invalidId)).willReturn(false);

        assertThatThrownBy(() -> concertService.getSchedules(invalidId))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_CONCERT);
    }
}
