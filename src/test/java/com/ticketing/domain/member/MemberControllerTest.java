package com.ticketing.domain.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.member.controller.MemberController;
import com.ticketing.domain.member.dto.MemberSignupRequest;
import com.ticketing.domain.member.dto.MemberSignupResponse;
import com.ticketing.domain.member.service.MemberService;
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
public class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberService memberService;

    @Test
    @DisplayName("회원가입 성공 테스트 - 201 Created 반환")
    @WithMockUser
    void signup_success() throws Exception {

        // given
        MemberSignupRequest request = new MemberSignupRequest();
        ReflectionTestUtils.setField(request, "email", "test@example.com");
        ReflectionTestUtils.setField(request, "password", "Password123!");
        ReflectionTestUtils.setField(request, "nickname", "테스터");

        MemberSignupResponse mockResponseDto = org.mockito.Mockito.mock(MemberSignupResponse.class);

        given(memberService.signUp(any(MemberSignupRequest.class))).willReturn(mockResponseDto);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        resultActions
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입 실패 테스트 - 이메일 누락 (400 Bad Request)")
    @WithMockUser
    void signup_validation_fail_email_null() throws Exception {
        // given
        MemberSignupRequest invalidRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(invalidRequest, "nickname", "테스터");
        ReflectionTestUtils.setField(invalidRequest, "password", "Password123!");
        // email 필드는 null (@NotBlank 위반)

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입 실패 테스트 - 비밀번호 누락 (400 Bad Request)")
    @WithMockUser
    void signup_validation_fail_password_null() throws Exception {
        // given
        MemberSignupRequest invalidRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(invalidRequest, "email", "test@example.com");
        ReflectionTestUtils.setField(invalidRequest, "nickname", "테스터");
        // password 필드는 null (@NotBlank 위반)

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andDo(print());
    }
}