package com.ticketing.domain.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.member.controller.MemberController;
import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.api.member.dto.MemberSignupResponse;
import com.ticketing.application.member.service.MemberService;
import com.ticketing.domain.member.model.Member;
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

import static com.ticketing.domain.member.exception.MemberErrorMessage.EMAIL_MUST_NOT_EMPTY;
import static com.ticketing.domain.member.exception.MemberErrorMessage.PSWD_MUST_NOT_EMPTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @DisplayName("\uD68C\uC6D0\uAC00\uC785 \uC131\uACF5 - 201 Created\uC640 \uC131\uACF5 \uC751\uB2F5 \uBC18\uD658")
    void signup_success() throws Exception {
        MemberSignupRequest request = new MemberSignupRequest();
        ReflectionTestUtils.setField(request, "email", "test@example.com");
        ReflectionTestUtils.setField(request, "password", "Password123!");
        ReflectionTestUtils.setField(request, "nickname", "\uD14C\uC2A4\uD2B8");

        Member member = Member.builder()
                .email("test@example.com")
                .nickname("\uD14C\uC2A4\uD2B8")
                .build();

        given(memberService.signUp(any(MemberSignupRequest.class)))
                .willReturn(new MemberSignupResponse(member));

        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("\uD14C\uC2A4\uD2B8"))
                .andDo(print());
    }

    @Test
    @WithMockUser
    @DisplayName("\uD68C\uC6D0\uAC00\uC785 \uC2E4\uD328 - \uC774\uBA54\uC77C \uB204\uB77D (400 Bad Request)")
    void signup_validation_fail_email_null() throws Exception {
        MemberSignupRequest invalidRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(invalidRequest, "nickname", "\uD14C\uC2A4\uD2B8");
        ReflectionTestUtils.setField(invalidRequest, "password", "Password123!");

        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.message").value("{email=" + EMAIL_MUST_NOT_EMPTY + "}"))
                .andDo(print());
    }

    @Test
    @WithMockUser
    @DisplayName("\uD68C\uC6D0\uAC00\uC785 \uC2E4\uD328 - \uBE44\uBC00\uBC88\uD638 \uB204\uB77D (400 Bad Request)")
    void signup_validation_fail_password_null() throws Exception {
        MemberSignupRequest invalidRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(invalidRequest, "email", "test@example.com");
        ReflectionTestUtils.setField(invalidRequest, "nickname", "\uD14C\uC2A4\uD2B8");

        ResultActions resultActions = mockMvc.perform(post("/api/v1/members/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.message").value("{password=" + PSWD_MUST_NOT_EMPTY + "}"))
                .andDo(print());
    }
}
