package com.ticketing.domain.queue.scheduler;

import com.ticketing.domain.queue.repository.QueueRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueScheduler {

    private final QueueRedisRepository queueRedisRepository;

    @Value("${queue.max-active-member:1000}")
    private long maxActiveMember;

    @Value("${queue.estimated-throughput:50}")
    private long enterBatchSize;

    @Value("${queue.active-time-ms:300000}")
    private long activeTimeMs;

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        Set<Long> scheduleIds = queueRedisRepository.getProcessingSchedules();
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return;
        }

        // 리턴값은 입장 성공한 유저 목록
        scheduleIds.parallelStream().forEach(scheduleId -> {
            try {
                List<String> enteredMembers = queueRedisRepository.moveUsersToActive(
                        scheduleId,
                        maxActiveMember,
                        enterBatchSize,
                        activeTimeMs
                );

                if (enteredMembers != null && !enteredMembers.isEmpty()) {
                    // 실제로 유저가 입장했을 때만 로그 출력
                    log.info("[Schedule {}] {}명 입장 성공 (Members: {})",
                            scheduleId, enteredMembers.size(), enteredMembers);
                }
            } catch (Exception e) {
                log.error("[Schedule {}] 대기열 처리 중 오류 발생", scheduleId, e);
            }
        });
    }
}