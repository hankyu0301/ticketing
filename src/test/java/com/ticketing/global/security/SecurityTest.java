package com.ticketing.global.security;

import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.application.member.service.MemberService;
import com.ticketing.application.queue.scheduler.QueueScheduler;
import com.ticketing.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SecurityTest extends IntegrationTestSupport {

    private static final String MEMBER_LOGIN_URL = "/api/v1/members/login";
    private static final String MEMBER_LOGIN_EMAIL = "test@test.com";
    private static final String MEMBER_LOGIN_PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MemberService memberService;

    @MockitoBean
    private QueueScheduler queueScheduler;

    @BeforeEach
    void beforeEach() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        MemberSignupRequest memberSignupRequest = new MemberSignupRequest();
        ReflectionTestUtils.setField(memberSignupRequest, "email", MEMBER_LOGIN_EMAIL);
        ReflectionTestUtils.setField(memberSignupRequest, "password", MEMBER_LOGIN_PASSWORD);
        ReflectionTestUtils.setField(memberSignupRequest, "nickname", "테스터");

        memberService.signUp(memberSignupRequest);
    }

    @Test
    @DisplayName("비회원은 홈에 접근할 수 있다.")
    @WithAnonymousUser
    void anonymousAuthorizationHome() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원은 홈에 접근할 수 있다.")
    @WithMockUser(authorities = "USER")
    void authenticationMember() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF bootstrap endpoint issues token and cookie")
    @WithAnonymousUser
    void csrfBootstrapEndpointIssuesTokenAndCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("익명 사용자는 회원가입할 수 있다.")
    @WithAnonymousUser
    void anonymousSignupSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "signup@test.com",
                                  "password": "password1234",
                                  "nickname": "익명가입"
                                }
                                """))
                .andDo(print())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비인증 사용자가 보호 API에 접근하면 JSON 401을 반환한다.")
    @WithAnonymousUser
    void anonymousProtectedApiReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/1/seats"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(LOGIN_REQUIRED));
    }

    @Test
    @DisplayName("로그인에 성공한다.")
    void loginSuccess() throws Exception {
        mockMvc.perform(post(MEMBER_LOGIN_URL)
                        .with(csrf())
                        .param("email", MEMBER_LOGIN_EMAIL)
                        .param("password", MEMBER_LOGIN_PASSWORD))
                .andDo(print())
                .andExpect(authenticated())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("USER 권한으로는 ADMIN 경로에 접근할 수 없다.")
    @WithMockUser(authorities = "USER")
    void endpointWithUserAuthorityAuthorized() throws Exception {
        mockMvc.perform(get("/admin"))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(AUTHORIZED_BUT_NOT_AUTHENTICATED));
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다.")
    void loginFailWithWrongPwsd() throws Exception {
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
    @DisplayName("존재하지 않는 회원으로 로그인하면 실패한다.")
    void loginFailNotExist() throws Exception {
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
    @DisplayName("로그아웃에 성공한다.")
    void logout() throws Exception {
        mockMvc.perform(post("/api/v1/members/logout").with(csrf()))
                .andDo(print())
                .andExpect(unauthenticated())
                .andExpect(status().isOk());
    }
}
