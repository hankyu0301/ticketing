package com.ticketing.domain.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.queue.controller.QueueController;
import com.ticketing.domain.queue.dto.QueueResponse;
import com.ticketing.domain.queue.repository.QueueRedisRepository;
import com.ticketing.domain.queue.service.QueueService;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.global.interceptor.QueueAccessInterceptor;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.SessionMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    QueueService queueService;

    @MockitoBean
    QueueAccessInterceptor queueAccessInterceptor;

    // 인증 객체 Mocking
    @BeforeEach
    void setup() {
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        SessionMember mockSession = mock(SessionMember.class);

        when(mockSession.getMemberId()).thenReturn(1L);
        when(mockUser.getSessionMember()).thenReturn(mockSession);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(mockUser, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("[SUCCESS] 대기열 진입 요청, 대기열 X 활성열 입장 - 200 OK")
    void enterQueue_activeMember_success() throws Exception {
        // given
        Long scheduleId = 100L;
        QueueResponse mockResponse = new QueueResponse(0L, 0L, true);
        given(queueService.enterQueue(anyLong(), anyLong())).willReturn(mockResponse);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/queue/enter")
                .param("scheduleId", String.valueOf(scheduleId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(0L))
                .andExpect(jsonPath("$.data.entered").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("[SUCCESS] 대기열 진입 요청, 대기열 진입 - 200 OK")
    void enterQueue_success() throws Exception {
        // given
        Long scheduleId = 100L;
        QueueResponse mockResponse = new QueueResponse(10L, 5L, false); // 10등, 5초 대기
        given(queueService.enterQueue(anyLong(), anyLong())).willReturn(mockResponse);

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/queue/enter")
                .param("scheduleId", String.valueOf(scheduleId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(10L))
                .andExpect(jsonPath("$.data.entered").value(false))
                .andDo(print());
    }

    @Test
    @DisplayName("[SUCCESS] 대기열 순번 조회 (대기중) - 200 OK")
    void getRank_success_waiting() throws Exception {
        // given
        Long scheduleId = 100L;
        QueueResponse mockResponse = new QueueResponse(10L, 5L, false); // 10등, 5초 대기

        given(queueService.getQueueStatus(anyLong(), anyLong())).willReturn(mockResponse);

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/queue/rank")
                .param("scheduleId", String.valueOf(scheduleId))
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(10L))
                .andExpect(jsonPath("$.data.entered").value(false))
                .andDo(print());
    }

    @Test
    @DisplayName("[FAIL] 대기열 조회 실패 - 대기열 없음 (404 Not Found)")
    void getRank_fail_notFound() throws Exception {
        // given
        Long scheduleId = 999L;
        given(queueService.getQueueStatus(anyLong(), anyLong()))
                .willThrow(new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE));

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/queue/rank")
                .param("scheduleId", String.valueOf(scheduleId))
                .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound()) // GlobalExceptionHandler 매핑 가정
                .andDo(print());
    }
}