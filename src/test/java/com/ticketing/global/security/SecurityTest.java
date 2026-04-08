package com.ticketing.global.security;

import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.application.member.service.MemberService;
import com.ticketing.application.queue.scheduler.QueueScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static com.ticketing.domain.member.exception.MemberErrorMessage.AUTHORIZED_BUT_NOT_AUTHENTICATED;
import static com.ticketing.domain.member.exception.MemberErrorMessage.CHECK_EMAIL_OR_PSWD;
import static com.ticketing.domain.member.exception.MemberErrorMessage.LOGIN_REQUIRED;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityTest {

    private static final String MEMBER_LOGIN_URL = "/api/v1/members/login";
    private static final String MEMBER_LOGIN_EMAIL = "test@test.com";
    private static final String MEMBER_LOGIN_PASSWORD = "password1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WebApplicationContext context;

    @Autowired
    MemberService memberService;

    @MockitoBean
    QueueScheduler queueScheduler;

    @BeforeEach
    void beforeEach() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        MemberSignupRequest memberSignupRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(memberSignupRequest, "email", MEMBER_LOGIN_EMAIL);
        ReflectionTestUtils.setField(memberSignupRequest, "password", MEMBER_LOGIN_PASSWORD);
        ReflectionTestUtils.setField(memberSignupRequest, "nickname", "tester");

        memberService.signUp(memberSignupRequest);
    }

    @Test
    @DisplayName("비회원은 홈에 접근할 수 있다")
    @WithAnonymousUser
    void anonymous_authorization_home() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원은 홈에 접근할 수 있다")
    @WithMockUser(authorities = "USER")
    void authentication_member() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비회원은 회원가입을 할 수 있다")
    @WithAnonymousUser
    void anonymous_signup_success() throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "signup@test.com",
                                  "password": "password1234",
                                  "nickname": "anonymous"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비인증 사용자가 보호 API에 접근하면 JSON 401을 받는다")
    @WithAnonymousUser
    void anonymous_protected_api_returns_json_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/1/seats"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(LOGIN_REQUIRED));
    }

    @Test
    @DisplayName("로그인에 성공하면 인증된다")
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
    @DisplayName("USER 권한으로 ADMIN 엔드포인트에 접근하면 JSON 403을 받는다")
    @WithMockUser(authorities = "USER")
    void endpoint_with_user_authority_authorized() throws Exception {
        mockMvc.perform(get("/admin"))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(AUTHORIZED_BUT_NOT_AUTHENTICATED));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인하면 JSON 401을 받는다")
    void login_fail_with_wrong_pwsd() throws Exception {
        mockMvc.perform(post(MEMBER_LOGIN_URL)
                        .with(csrf())
                        .param("email", MEMBER_LOGIN_EMAIL)
                        .param("password", "ab"))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(CHECK_EMAIL_OR_PSWD));
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 로그인하면 JSON 401을 받는다")
    void login_fail_not_exist() throws Exception {
        mockMvc.perform(post(MEMBER_LOGIN_URL)
                        .with(csrf())
                        .param("email", "no-user@test.com")
                        .param("password", MEMBER_LOGIN_PASSWORD))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(CHECK_EMAIL_OR_PSWD));
    }

    @Test
    @DisplayName("로그아웃에 성공한다")
    void logout() throws Exception {
        mockMvc.perform(post("/api/v1/members/logout").with(csrf()))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().isOk());
    }
}
