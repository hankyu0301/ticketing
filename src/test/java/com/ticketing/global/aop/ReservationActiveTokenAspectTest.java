package com.ticketing.global.aop;

import com.ticketing.api.reservation.controller.ReservationController;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.SessionMember;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReservationActiveTokenAspectTest {

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private QueueValidationAspect queueValidationAspect;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("예약 요청은 request.scheduleId로 active token을 검증한다")
    void validateQueueToken_success() throws NoSuchMethodException {
        ReservationCreateRequest request = createRequest(7L, 3L);
        CustomUserDetails userDetails = authenticate(1L);
        Method method = ReservationController.class.getMethod(
                "createReservation",
                CustomUserDetails.class,
                ReservationCreateRequest.class
        );

        given(joinPoint.getSignature()).willReturn(methodSignature);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{userDetails, request});
        given(queueRedisRepository.validateAndExtendActiveMember(7L, 1L)).willReturn(true);

        assertThatCode(() -> queueValidationAspect.validateQueueToken(joinPoint))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("active token이 없으면 예약 요청은 차단된다")
    void validateQueueToken_fail_whenQueueIsNotActive() throws NoSuchMethodException {
        ReservationCreateRequest request = createRequest(7L, 3L);
        CustomUserDetails userDetails = authenticate(1L);
        Method method = ReservationController.class.getMethod(
                "createReservation",
                CustomUserDetails.class,
                ReservationCreateRequest.class
        );

        given(joinPoint.getSignature()).willReturn(methodSignature);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{userDetails, request});
        given(queueRedisRepository.validateAndExtendActiveMember(7L, 1L)).willReturn(false);

        assertThatThrownBy(() -> queueValidationAspect.validateQueueToken(joinPoint))
                .isInstanceOf(QueueNotFoundException.class);
    }

    private ReservationCreateRequest createRequest(Long scheduleId, Long seatId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return request;
    }

    private CustomUserDetails authenticate(Long memberId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                SessionMember.create(memberId, "user@test.com", "tester"),
                "password",
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
        return userDetails;
    }
}
