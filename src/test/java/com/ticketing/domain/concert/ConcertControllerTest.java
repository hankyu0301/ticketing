package com.ticketing.domain.concert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.concert.controller.ConcertController;
import com.ticketing.api.concert.dto.ConcertCreateRequest;
import com.ticketing.api.concert.dto.ConcertDto;
import com.ticketing.api.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.application.concert.service.ConcertService;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConcertController.class)
class ConcertControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ConcertService concertService;

    // ==========================================
    // 1. 콘서트 생성 테스트
    // ==========================================

    @Test
    @DisplayName("[SUCCESS] 콘서트 생성 성공 - 201 Created")
    @WithMockUser
    void createConcert_success() throws Exception {
        // given
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "title", "아이유 콘서트");
        ReflectionTestUtils.setField(request, "description", "상암 월드컵 경기장");

        given(concertService.createConcert(any(ConcertCreateRequest.class))).willReturn(1L);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated());
    }

    @Test
    @DisplayName("[FAIL] 콘서트 생성 실패 - title 누락 (400 Bad Request)")
    @WithMockUser
    void createConcert_fail_null_title() throws Exception {

        // given
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "description", "설명만 있음");

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 콘서트 생성 실패 - description 누락 (400 Bad Request)")
    @WithMockUser
    void createConcert_fail_null_description() throws Exception {

        // given
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "title", "제목만 있음");

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    // ==========================================
    // 2. 스케줄 생성 테스트
    // ==========================================

    @Test
    @DisplayName("[SUCCESS] 스케줄 생성 성공 - 201 Created")
    @WithMockUser
    void createSchedule_success() throws Exception {
        // given
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated());
    }

    @Test
    @DisplayName("[FAIL] 스케줄 생성 실패 - 존재하지 않는 콘서트 (404 Not Found)")
    @WithMockUser
    void createSchedule_fail_concertNotFound() throws Exception {
        // given
        Long invalidConcertId = 999L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(invalidConcertId), any(ConcertScheduleCreateRequest.class)))
                .willThrow(new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", invalidConcertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isNotFound())
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 스케줄 생성 실패 - concertDate 누락 (400 Bad Request)")
    @WithMockUser
    void createSchedule_fail_null_concertDate() throws Exception {

        // given
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 스케줄 생성 실패 - reservationOpenAt 누락 (400 Bad Request)")
    @WithMockUser
    void createSchedule_fail_null_reservationOpenAt() throws Exception {

        // given
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 스케줄 생성 실패 - totalSeats 누락 (400 Bad Request)")
    @WithMockUser
    void createSchedule_fail_null_totalSeats() throws Exception {

        // given
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 스케줄 생성 실패 - totalSeats 음수 (400 Bad Request)")
    @WithMockUser
    void createSchedule_fail_negative_totalSeats() throws Exception {

        // given
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", -1);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    // ==========================================
    // 3. 콘서트 조회 테스트
    // ==========================================
    @Test
    @DisplayName("[SUCCESS] 콘서트 상세 조회 성공 - 200 OK")
    @WithMockUser
    void getConcert_success() throws Exception {
        // given
        Long concertId = 1L;
        Concert mockConcert = Concert.create("아이유 콘서트", "설명");
        ConcertDto concertDto = new ConcertDto(mockConcert);
        ReflectionTestUtils.setField(mockConcert, "concertId", concertId);

        given(concertService.getConcert(concertId)).willReturn(concertDto);

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/concerts/{concertId}", concertId)
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("[FAIL] 콘서트 상세 조회 실패 - 존재하지 않는 콘서트 (404 Not Found)")
    @WithMockUser
    void getConcert_fail_notFound() throws Exception {
        // given
        Long invalidId = 999L;
        given(concertService.getConcert(invalidId))
                .willThrow(new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/concerts/{concertId}", invalidId)
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andDo(print());
    }

    // ==========================================
    // 4. 스케줄 목록 조회 테스트
    // ==========================================

    @Test
    @DisplayName("[FAIL] 스케줄 목록 조회 실패 - 존재하지 않는 콘서트 (404 Not Found)")
    @WithMockUser
    void getSchedules_fail_concertNotFound() throws Exception {
        // given
        Long invalidId = 999L;
        given(concertService.getSchedules(invalidId))
                .willThrow(new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/concerts/{concertId}/schedules", invalidId)
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andDo(print());
    }
}