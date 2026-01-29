package com.ticketing.domain.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.reservation.controller.ReservationController;
import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.service.ReservationService;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.SessionMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
public class ReservationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ReservationService reservationService;

    @Test
    @DisplayName("예약 성공 테스트 - 201 Created 반환")
    void create_success() throws Exception {

        // given
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", 1L);
        ReflectionTestUtils.setField(request, "seatId", 1L);

        Long memberId = 1L;
        CustomUserDetails mockPrincipal = org.mockito.Mockito.mock(CustomUserDetails.class);
        SessionMember mockSessionMember = org.mockito.Mockito.mock(SessionMember.class);

        given(mockPrincipal.getSessionMember()).willReturn(mockSessionMember);
        given(mockSessionMember.getMemberId()).willReturn(memberId);
        given(mockPrincipal.getAuthorities()).willReturn(Collections.emptyList());

        Authentication auth = new UsernamePasswordAuthenticationToken(mockPrincipal, null, mockPrincipal.getAuthorities());

        ReservationResponse mockResponseDto = org.mockito.Mockito.mock(ReservationResponse.class);

        given(reservationService.reserve(anyLong(), any(ReservationCreateRequest.class))).willReturn(mockResponseDto);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/v1/reservations")
                .with(csrf())
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        resultActions
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @DisplayName("예약 실패 테스트 - 스케줄 ID 누락 (400 Bad Request)")
    @WithMockUser
    void create_validation_fail_scheduleId_null() throws Exception {
        // given
        ReservationCreateRequest invalidRequest = new ReservationCreateRequest();
        ReflectionTestUtils.setField(invalidRequest, "seatId", 1L);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/v1/reservations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("예약 실패 테스트 - 좌석 ID 누락 (400 Bad Request)")
    @WithMockUser
    void create_validation_fail_seatId_null() throws Exception {
        // given
        ReservationCreateRequest invalidRequest = new ReservationCreateRequest();
        ReflectionTestUtils.setField(invalidRequest, "scheduleId", 1L);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/v1/reservations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andDo(print());
    }
}