package com.ticketing.domain.concert;

import com.ticketing.api.concert.dto.ConcertCreateRequest;
import com.ticketing.api.concert.dto.ConcertDto;
import com.ticketing.api.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.api.concert.dto.ConcertScheduleDto;
import com.ticketing.infrastructure.persistence.concert.ConcertMapper;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.application.concert.service.ConcertService;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertNotFoundException;
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

    // ==========================================
    // 1. 콘서트 생성 테스트
    // ==========================================

    @Test
    @DisplayName("[SUCCESS] 콘서트 생성")
    void createConcert_success() {
        // given
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "title", "테스트 콘서트");
        ReflectionTestUtils.setField(request, "description", "설명");

        // Insert 시 ID 주입
        doAnswer(invocation -> {
            Concert argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "concertId", 1L);
            return null;
        }).when(concertMapper).insertConcert(any(Concert.class));

        // when
        Long resultId = concertService.createConcert(request);

        // then
        assertThat(resultId).isEqualTo(1L);
        verify(concertMapper, times(1)).insertConcert(any(Concert.class));
    }

    // ==========================================
    // 2. 스케줄 생성 테스트
    // ==========================================

    @Test
    @DisplayName("[SUCCESS] 스케줄 생성 성공")
    void createSchedule_success() {
        // given
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now());
        ReflectionTestUtils.setField(request, "totalSeats", 100);

        given(concertMapper.existsById(concertId)).willReturn(true);

        // Insert 시 ID 주입
        doAnswer(invocation -> {
            ConcertSchedule argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "scheduleId", 10L);
            return null;
        }).when(concertScheduleMapper).insertSchedule(any(ConcertSchedule.class));

        // when
        Long scheduleId = concertService.createSchedule(concertId, request);

        // then
        assertThat(scheduleId).isEqualTo(10L);
        verify(concertScheduleMapper).insertSchedule(any(ConcertSchedule.class));
    }

    @Test
    @DisplayName("[FAIL] 스케줄 생성 실패 - 존재하지 않는 콘서트 (404 Not Found)")
    void createSchedule_fail_concertNotFound() {
        // given
        Long invalidConcertId = 999L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();

        given(concertMapper.existsById(invalidConcertId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> concertService.createSchedule(invalidConcertId, request))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_CONCERT);

        verify(concertScheduleMapper, never()).insertSchedule(any(ConcertSchedule.class));
    }

    // ==========================================
    // 3. 콘서트 조회 테스트
    // ==========================================

    @Test
    @DisplayName("[SUCCESS] 콘서트 조회 성공")
    void getConcert_success() {
        // given
        Long concertId = 1L;
        Concert concert = Concert.create("조회된 콘서트", "설명");
        ReflectionTestUtils.setField(concert, "concertId", concertId);

        given(concertMapper.findById(concertId)).willReturn(Optional.of(concert));

        // when
        ConcertDto result = concertService.getConcert(concertId);

        // then
        assertThat(result.getConcertId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("조회된 콘서트");
    }

    @Test
    @DisplayName("[FAIL] 콘서트 조회 실패 - 존재하지 않는 콘서트 (404 Not Found)")
    void getConcert_fail_notFound() {
        // given
        Long invalidId = 999L;
        given(concertMapper.findById(invalidId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> concertService.getConcert(invalidId))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage(ConcertErrorMessage.NOT_EXIST_CONCERT);
    }

    // ==========================================
    // 4. 스케줄 목록 조회 테스트
    // ==========================================

    @Test
    @DisplayName("[SUCCESS] 스케줄 목록 조회 성공")
    void getSchedules_success() {
        // given
        Long concertId = 1L;
        ConcertSchedule schedule = ConcertSchedule.create(concertId, LocalDateTime.now(), LocalDateTime.now(), 100);

        given(concertScheduleMapper.findAllByConcertId(concertId)).willReturn(List.of(schedule));

        // when
        List<ConcertScheduleDto> result = concertService.getSchedules(concertId);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("[FAIL] 스케줄 목록 조회 실패 - 존재하지 않는 콘서트")
    void getSchedules_fail_concertNotFound() {
        // given
        Long invalidId = 999L;

        // when & then
        List<ConcertScheduleDto> result = concertService.getSchedules(invalidId);

        assertThat(result).isEmpty();
    }
}