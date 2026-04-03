package com.ticketing.application.queue.scheduler;

import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueScheduler {

    private final QueueRedisRepository queueRedisRepository;

    @Value("${queue.max-active-member:1000}")
    private long maxActiveMember;

    @Value("${queue.enter-batch-size:50}")
    private long enterBatchSize;

    @Value("${queue.active-time-ms:300000}")
    private long activeTimeMs;

    @Scheduled(fixedDelayString = "${scheduler.fixed-delay:1000}")
    @SchedulerLock(
            name = "QueueScheduler_processQueue",
            lockAtLeastFor = "${scheduler.lock-at-least-for:PT1S}",
            lockAtMostFor = "${scheduler.lock-at-most-for:PT10S}"
    )
    public void processQueue() {
        Set<Long> scheduleIds = queueRedisRepository.getProcessingSchedules();
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return;
        }

        long startedAt = System.nanoTime();
        int processedSchedules = 0;
        int enteredMemberCount = 0;

        for (Long scheduleId : scheduleIds) {
            processedSchedules++;
            try {
                List<String> enteredMembers = queueRedisRepository.moveUsersToActive(
                        scheduleId,
                        maxActiveMember,
                        enterBatchSize,
                        activeTimeMs
                );

                if (enteredMembers != null && !enteredMembers.isEmpty()) {
                    enteredMemberCount += enteredMembers.size();
                    log.info("[Schedule {}] {} users entered (Members: {})",
                            scheduleId, enteredMembers.size(), enteredMembers);
                }
            } catch (Exception e) {
                log.error("[Schedule {}] queue processing failed", scheduleId, e);
            }
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.debug("QueueScheduler completed. processedSchedules={}, enteredMemberCount={}, elapsedMs={}",
                processedSchedules, enteredMemberCount, elapsedMs);
    }
}
