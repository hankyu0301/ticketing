package com.ticketing.domain.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.member.controller.MemberController;
import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.api.member.dto.MemberSignupResponse;
import com.ticketing.application.member.service.MemberService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @Test
    @WithMockUser
    @DisplayName("회원가입 성공 - 201 Created 반환")
    void signup_success() throws Exception {
        MemberSignupRequest request = new MemberSignupRequest();
        ReflectionTestUtils.setField(request, "email", "test@example.com");
        ReflectionTestUtils.setField(request, "password", "Password123!");
        ReflectionTestUtils.setField(request, "nickname", "테스트");

        MemberSignupResponse mockResponseDto = org.mockito.Mockito.mock(MemberSignupResponse.class);
        given(memberService.signUp(any(MemberSignupRequest.class))).willReturn(mockResponseDto);

        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        resultActions
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @WithMockUser
    @DisplayName("회원가입 실패 테스트 - 이메일 누락 (400 Bad Request)")
    void signup_validation_fail_email_null() throws Exception {
        MemberSignupRequest invalidRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(invalidRequest, "nickname", "테스트");
        ReflectionTestUtils.setField(invalidRequest, "password", "Password123!");

        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        resultActions
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @WithMockUser
    @DisplayName("회원가입 실패 테스트 - 비밀번호 누락 (400 Bad Request)")
    void signup_validation_fail_password_null() throws Exception {
        MemberSignupRequest invalidRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(invalidRequest, "email", "test@example.com");
        ReflectionTestUtils.setField(invalidRequest, "nickname", "테스트");

        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        resultActions
                .andExpect(status().isBadRequest())
                .andDo(print());
    }
}
