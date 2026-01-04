package com.ticketing.domain.queue.scheduler;

import com.ticketing.domain.queue.repository.QueueRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    @Scheduled(fixedDelayString = "${scheduler.fixed-delay:1000}")
    @SchedulerLock(
            name = "QueueScheduler_processQueue", // 락 이름
            lockAtLeastFor = "990ms", // 작업이 빨리 끝나도 0.99초간은 락을 쥐고 있음 (중복 실행 방지)
            lockAtMostFor = "2000ms"  // 2초가 지나면 락을 강제 해제 (서버 다운 대비)
    )
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