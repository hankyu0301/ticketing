package com.ticketing.api.concert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.concert.controller.ConcertController;
import com.ticketing.api.concert.dto.ConcertCreateRequest;
import com.ticketing.api.concert.dto.ConcertDto;
import com.ticketing.api.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.domain.concert.exception.ConcertBadRequestException;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertNotFoundException;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.application.concert.service.ConcertService;
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConcertService concertService;

    @Test
    @DisplayName("콘서트를 생성하면 201 Created를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createConcertSuccess() throws Exception {
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "title", "아이유 콘서트");
        ReflectionTestUtils.setField(request, "description", "상암 월드컵 경기장");

        given(concertService.createConcert(any(ConcertCreateRequest.class))).willReturn(1L);

        ResultActions result = mockMvc.perform(post("/api/v1/concerts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isCreated());
    }

    @Test
    @DisplayName("콘서트 생성 요청에 title이 없으면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createConcertFailNullTitle() throws Exception {
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "description", "설명만 있음");

        ResultActions result = mockMvc.perform(post("/api/v1/concerts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("콘서트 생성 요청에 description이 없으면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createConcertFailNullDescription() throws Exception {
        ConcertCreateRequest request = new ConcertCreateRequest();
        ReflectionTestUtils.setField(request, "title", "제목만 있음");

        ResultActions result = mockMvc.perform(post("/api/v1/concerts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("스케줄을 생성하면 201 Created를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleSuccess() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isCreated());
    }

    @Test
    @DisplayName("존재하지 않는 콘서트에 스케줄 생성을 요청하면 404 Not Found를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleFailConcertNotFound() throws Exception {
        Long invalidConcertId = 999L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(invalidConcertId), any(ConcertScheduleCreateRequest.class)))
                .willThrow(new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", invalidConcertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isNotFound())
                .andDo(print());
    }

    @Test
    @DisplayName("스케줄 생성 요청에 concertDate가 없으면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleFailNullConcertDate() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("스케줄 생성 요청에 reservationOpenAt이 없으면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleFailNullReservationOpenAt() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("스케줄 생성 요청에 totalSeats가 없으면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleFailNullTotalSeats() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("스케줄 생성 요청의 totalSeats가 음수면 400 Bad Request를 반환한다.")
    @WithMockUser
    void createScheduleFailNegativeTotalSeats() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "totalSeats", -1);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class))).willReturn(10L);

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("예매 오픈 시간이 과거인 스케줄 생성을 요청하면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleFailPastReservationOpenAt() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class)))
                .willThrow(new ConcertBadRequestException(ConcertErrorMessage.RESERVATION_OPEN_AT_MUST_BE_FUTURE));

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("예매 오픈 시간이 공연 시작 이후인 스케줄 생성을 요청하면 400 Bad Request를 반환한다.")
    @WithMockUser(roles = "ADMIN")
    void createScheduleFailReservationOpenAtAfterConcertDate() throws Exception {
        Long concertId = 1L;
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(10));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now().plusDays(11));
        ReflectionTestUtils.setField(request, "totalSeats", 50000);

        given(concertService.createSchedule(eq(concertId), any(ConcertScheduleCreateRequest.class)))
                .willThrow(new ConcertBadRequestException(ConcertErrorMessage.RESERVATION_OPEN_AT_MUST_BE_BEFORE_CONCERT_DATE));

        ResultActions result = mockMvc.perform(post("/api/v1/concerts/{concertId}/schedules", concertId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("콘서트 상세를 조회하면 200 OK를 반환한다.")
    @WithMockUser
    void getConcertSuccess() throws Exception {
        Long concertId = 1L;
        Concert mockConcert = Concert.create("아이유 콘서트", "설명");
        ConcertDto concertDto = new ConcertDto(mockConcert);
        ReflectionTestUtils.setField(mockConcert, "concertId", concertId);

        given(concertService.getConcert(concertId)).willReturn(concertDto);

        ResultActions result = mockMvc.perform(get("/api/v1/concerts/{concertId}", concertId)
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 상세를 조회하면 404 Not Found를 반환한다.")
    @WithMockUser
    void getConcertFailNotFound() throws Exception {
        Long invalidId = 999L;
        given(concertService.getConcert(invalidId))
                .willThrow(new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));

        ResultActions result = mockMvc.perform(get("/api/v1/concerts/{concertId}", invalidId)
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound())
                .andDo(print());
    }

    @Test
    @DisplayName("존재하지 않는 콘서트의 스케줄 목록을 조회하면 404 Not Found를 반환한다.")
    @WithMockUser
    void getSchedulesFailConcertNotFound() throws Exception {
        Long invalidId = 999L;
        given(concertService.getSchedules(invalidId))
                .willThrow(new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));

        ResultActions result = mockMvc.perform(get("/api/v1/concerts/{concertId}/schedules", invalidId)
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound())
                .andDo(print());
    }
}
