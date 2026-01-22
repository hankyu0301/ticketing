package com.ticketing.global.interceptor;

import com.ticketing.domain.queue.repository.QueueRedisRepository;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.SessionMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueAccessInterceptorTest {

    @InjectMocks
    QueueAccessInterceptor interceptor;

    @Mock
    QueueRedisRepository queueRedisRepository;

    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setup() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        SessionMember mockSession = mock(SessionMember.class);

        when(mockSession.getMemberId()).thenReturn(1L);
        when(mockUser.getSessionMember()).thenReturn(mockSession);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(mockUser, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("[SUCCESS] 활성 회원 여부 검증 성공")
    void preHandle_success() {
        // given
        Long scheduleId = 1L;
        Map<String, String> pathVariables = Map.of("scheduleId", String.valueOf(scheduleId));
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);

        given(queueRedisRepository.validateAndExtendActiveMember(anyLong(), anyLong()))
                .willReturn(true);

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[FAIL] 활성 회원 여부 검증 실패")
    void preHandle_fail_not_active() {
        // given
        Map<String, String> pathVariables = Map.of("scheduleId", "1");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);

        given(queueRedisRepository.validateAndExtendActiveMember(anyLong(), anyLong()))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    @DisplayName("[SUCCESS] URL 패턴 불일치")
    void preHandle_skip_validation() {
        // given
        // PathVariable 설정 안 함 (null)

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }
}