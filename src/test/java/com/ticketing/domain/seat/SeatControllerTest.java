package com.ticketing.domain.seat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.seat.controller.SeatController;
import com.ticketing.domain.seat.dto.SeatCreateRequest;
import com.ticketing.domain.seat.dto.SeatResponse;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.domain.seat.service.SeatService;
import com.ticketing.global.interceptor.QueueAccessInterceptor;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeatController.class)
class SeatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    SeatService seatService;

    @MockitoBean
    QueueAccessInterceptor queueAccessInterceptor;

    @Test
    @DisplayName("[SUCCESS] 좌석 생성 API 성공")
    @WithMockUser
    void createSeats_success() throws Exception {
        // given
        Long scheduleId = 1L;
        SeatCreateRequest request = new SeatCreateRequest();
        ReflectionTestUtils.setField(request, "totalSeats", 50);
        ReflectionTestUtils.setField(request, "grade", "VIP");
        ReflectionTestUtils.setField(request, "price", BigDecimal.valueOf(150000));

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/schedules/{scheduleId}/seats", scheduleId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("[SUCCESS] 좌석 배치도 조회 API 성공")
    @WithMockUser
    void getSeats_success() throws Exception {
        // given
        Long scheduleId = 1L;

        Seat seat1 = Seat.create(scheduleId, 1, "VIP", BigDecimal.valueOf(150000));
        Seat seat2 = Seat.create(scheduleId, 2, "VIP", BigDecimal.valueOf(150000));
        ReflectionTestUtils.setField(seat2, "status", Seat.SeatStatus.SOLD_OUT);

        List<SeatResponse> response = List.of(new SeatResponse(seat1), new SeatResponse(seat2));

        given(seatService.getSeats(anyLong())).willReturn(response);
        given(queueAccessInterceptor.preHandle(any(), any(), any())).willReturn(true);

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/schedules/{scheduleId}/seats", scheduleId)
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(2))
                .andExpect(jsonPath("$.data[0].seatNo").value(1))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[1].seatNo").value(2))
                .andExpect(jsonPath("$.data[1].status").value("SOLD_OUT"))
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 좌석 생성 실패 : 유효성 검증 실패")
    @WithMockUser
    void createSeats_fail_validation() throws Exception {
        // given
        Long scheduleId = 1L;
        SeatCreateRequest request = new SeatCreateRequest();

        ReflectionTestUtils.setField(request, "totalSeats", -5);
        ReflectionTestUtils.setField(request, "grade", "");
        ReflectionTestUtils.setField(request, "price", BigDecimal.valueOf(-1000));

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/schedules/{scheduleId}/seats", scheduleId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest())
                .andDo(print());
    }
}