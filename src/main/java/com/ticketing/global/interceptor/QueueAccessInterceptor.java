package com.ticketing.global.interceptor;

import com.ticketing.domain.queue.repository.QueueRedisRepository;
import com.ticketing.global.common.exception.queue.QueueErrorMessage;
import com.ticketing.global.common.exception.queue.QueueNotFoundException;
import com.ticketing.global.security.service.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAccessInterceptor implements HandlerInterceptor {

    private final QueueRedisRepository queueRedisRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long memberId = ((CustomUserDetails) authentication.getPrincipal()).getSessionMember().getMemberId();

        // URL에서 scheduleId 추출
        // /api/v1/concerts/{concertId}/schedules/{scheduleId}/seats
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables == null || !pathVariables.containsKey("scheduleId")) {
            return true; // 스케줄 ID가 없는 요청은 검증 대상 아님
        }

        Long scheduleId = Long.parseLong(pathVariables.get("scheduleId"));

        // Redis 조회 및 연장 (검증 로직)
        // Redis에 키가 있으면 TTL 연장하고 true 반환, 없으면 false
        boolean isActive = queueRedisRepository.validateAndExtendActiveMember(scheduleId, memberId);
        if (!isActive) {
            log.warn("접근 차단: 활성열에 존재하지 않는 유저 (User: {}, Schedule: {})", memberId, scheduleId);
            throw new QueueNotFoundException(QueueErrorMessage.NOT_EXIST_QUEUE);
        }
        log.info("접근 허용 & 시간 연장 (User: {}, Schedule: {})", memberId, scheduleId);
        return true;
    }
}