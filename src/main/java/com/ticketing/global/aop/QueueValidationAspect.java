package com.ticketing.global.aop;

import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class QueueValidationAspect {

    private final QueueRedisRepository queueRedisRepository;

    @Before("@annotation(com.ticketing.global.aop.ActiveTokenRequired)")
    public void validateQueueToken(JoinPoint joinPoint) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long memberId = userDetails.getSessionMember().getMemberId();

        Long scheduleId = extractScheduleId(joinPoint);
        if (scheduleId == null) {
            throw new IllegalArgumentException("대기열 검증에 필요한 scheduleId를 찾을 수 없습니다.");
        }

        boolean isActive = queueRedisRepository.validateAndExtendActiveMember(scheduleId, memberId);
        if (!isActive) {
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        log.info("[AOP] 대기열 검증 통과 (User: {}, Schedule: {})", memberId, scheduleId);
    }

    private Long extractScheduleId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].isAnnotationPresent(ScheduleIdParam.class)) {
                continue;
            }

            Object arg = args[i];
            if (arg instanceof Long scheduleId) {
                return scheduleId;
            }
            if (arg instanceof ScheduleIdHolder scheduleIdHolder) {
                return scheduleIdHolder.getScheduleId();
            }

            throw new IllegalArgumentException("@ScheduleIdParam 파라미터는 Long 또는 ScheduleIdHolder여야 합니다.");
        }

        return null;
    }
}
