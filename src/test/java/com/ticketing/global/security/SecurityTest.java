package com.ticketing.global.security;

import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.application.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SecurityTest {

    private static final String MEMBER_LOGIN_URL = "/api/v1/members/login";
    private static final String MEMBER_LOGIN_EMAIL = "test@test.com";
    private static final String MEMBER_LOGIN_PASSWORD = "password1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WebApplicationContext context;

    @Autowired
    MemberService memberService;

    @BeforeEach
    void beforeEach() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        MemberSignupRequest memberSignupRequest =  new MemberSignupRequest();

        ReflectionTestUtils.setField(memberSignupRequest, "email", MEMBER_LOGIN_EMAIL);
        ReflectionTestUtils.setField(memberSignupRequest, "password", MEMBER_LOGIN_PASSWORD);
        ReflectionTestUtils.setField(memberSignupRequest, "nickname", "테스터");

        memberService.signUp(memberSignupRequest);
    }

    @Test
    @DisplayName("[SUCCESS] 비회원 권한으로 홈 접근")
    @WithAnonymousUser
    void anonymous_authorization_home() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk());
    }
    @Test
    @DisplayName("[SUCCESS] 회원 권한으로 홈 접근")
    @WithMockUser(authorities = "USER")
    void authentication_member() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[SUCCESS] 로그인 성공")
    void login_success() throws Exception {
        mockMvc.perform(post(MEMBER_LOGIN_URL)
                        .with(csrf())
                        .param("email", MEMBER_LOGIN_EMAIL)
                        .param("password", MEMBER_LOGIN_PASSWORD))
                .andDo(print())
                .andExpect(authenticated())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("[ERROR] 접근 권한 없음 : USER가 ADMIN 권한으로 접근")
    @WithMockUser(authorities="USER")
    void endpoint_with_user_authority_authorized() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[ERROR] 로그인 실패 : 비밀번호 불일치")
    public void login_fail_with_wrong_pwsd() throws Exception {
        String invalidPassword = "ab";
        mockMvc.perform(post(MEMBER_LOGIN_URL)
                        .with(csrf())
                        .param("email", MEMBER_LOGIN_EMAIL)
                        .param("password", invalidPassword))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("[ERROR] 로그인 실패 : 존재하지 않는 유저")
    void login_fail_not_exist() throws Exception{
        String invalidEmail = "존재하지 않는 유저";
        mockMvc.perform(post(MEMBER_LOGIN_URL)
                        .with(csrf())
                        .param("email", invalidEmail)
                        .param("password", MEMBER_LOGIN_PASSWORD))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("[SUCCESS] 로그아웃")
    void logout() throws Exception{
        mockMvc.perform(post("/api/v1/members/logout").with(csrf()))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().isOk());
    }
}
