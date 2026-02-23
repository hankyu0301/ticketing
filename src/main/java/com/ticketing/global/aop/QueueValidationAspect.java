package com.ticketing.global.aop;

import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.global.security.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class QueueValidationAspect {

    private final QueueRedisRepository queueRedisRepository;

    @Before("@annotation(com.ticketing.global.aop.ActiveTokenRequired)")
    public void validateQueueToken(JoinPoint joinPoint) {
        // 1. 현재 로그인한 사용자 ID 가져오기 (SecurityContext)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long memberId = userDetails.getSessionMember().getMemberId();

        // 2. 메서드 인자(Arguments) 중에서 scheduleId를 가진 DTO 찾기
        Long scheduleId = findScheduleIdFromArgs(joinPoint.getArgs());

        if (scheduleId == null) {
            throw new IllegalArgumentException("대기열 검증 실패: scheduleId를 찾을 수 없습니다.");
        }

        // 3. Redis 검증 로직 수행
        boolean isActive = queueRedisRepository.validateAndExtendActiveMember(scheduleId, memberId);
        if (!isActive) {
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }

        log.info("[AOP] 대기열 검증 통과 (User: {}, Schedule: {})", memberId, scheduleId);
    }

    private Long findScheduleIdFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg == null) continue;

            if (arg instanceof Long) {
                return (Long) arg;
            }

            try {
                Method method = arg.getClass().getMethod("getScheduleId");
                return (Long) method.invoke(arg);
            } catch (Exception e) {
                // 이 객체엔 getScheduleId가 없음 -> 다음 인자 탐색
                continue;
            }
        }
        return null;
    }
}