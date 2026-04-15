package com.ticketing.infrastructure.persistence.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private final RedisScript<Long> queueEnterScript;
    private final RedisScript<Long> queueStatusScript;
    private final RedisScript<List> activeMemberScript;
    private final RedisScript<Long> validExtensionScript;
    private final RedisScript<Long> cleanupProcessingScheduleScript;

    private static final String PROCESSING_SCHEDULES_KEY = "queue:processing_schedules";
    private static final String META_RESERVATION_OPEN_AT = "reservationOpenAtMs";
    private static final String META_CONCERT_DATE = "concertDateMs";

    @Value("${queue.safety-ttl-days:7}")
    private long safetyTtlDays;

    @Value("${queue.active-time-ms:300000}")
    private long activeTimeMs;

    public void saveScheduleMeta(Long scheduleId, long reservationOpenAtMs, long concertDateMs) {
        redisTemplate.opsForHash().putAll(
                getMetaKey(scheduleId),
                Map.of(
                        META_RESERVATION_OPEN_AT, String.valueOf(reservationOpenAtMs),
                        META_CONCERT_DATE, String.valueOf(concertDateMs)
                )
        );
    }

    public Optional<QueueScheduleMeta> findScheduleMeta(Long scheduleId) {
        Object reservationOpenAt = redisTemplate.opsForHash().get(getMetaKey(scheduleId), META_RESERVATION_OPEN_AT);
        Object concertDate = redisTemplate.opsForHash().get(getMetaKey(scheduleId), META_CONCERT_DATE);

        if (reservationOpenAt == null || concertDate == null) {
            return Optional.empty();
        }

        return Optional.of(new QueueScheduleMeta(
                scheduleId,
                Long.parseLong(reservationOpenAt.toString()),
                Long.parseLong(concertDate.toString())
        ));
    }

    public Long register(Long scheduleId, Long memberId, long maxActive, long activeTimeMs) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        String scoreKey = getScoreKey(scheduleId);
        String metaKey = getMetaKey(scheduleId);
        long now = System.currentTimeMillis();
        long expireAt = now + activeTimeMs;
        long ttlSeconds = safetyTtlDays * 24 * 60 * 60;

        return redisTemplate.execute(
                queueEnterScript,
                List.of(waitKey, activeKey, scoreKey, metaKey),
                memberId.toString(),
                String.valueOf(maxActive),
                String.valueOf(expireAt),
                String.valueOf(ttlSeconds),
                String.valueOf(now)
        );
    }

    public Long getRank(Long scheduleId, Long memberId) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        String metaKey = getMetaKey(scheduleId);

        Long rank = redisTemplate.execute(
                queueStatusScript,
                List.of(waitKey, activeKey, metaKey),
                memberId.toString(),
                String.valueOf(System.currentTimeMillis())
        );

        if (rank != null && rank == -1L) {
            cleanupProcessingScheduleIfDrained(scheduleId);
        }

        return rank;
    }

    public boolean validateAndExtendActiveMember(Long scheduleId, Long memberId) {
        String activeKey = getActiveKey(scheduleId);
        long currentTime = System.currentTimeMillis();
        long newExpireAt = currentTime + activeTimeMs;

        Long result = redisTemplate.execute(
                validExtensionScript,
                List.of(activeKey),
                memberId.toString(),
                String.valueOf(currentTime),
                String.valueOf(newExpireAt)
        );

        boolean isActive = result != null && result == 1L;
        if (!isActive) {
            cleanupProcessingScheduleIfDrained(scheduleId);
        }
        return isActive;
    }

    public List<String> moveUsersToActive(Long scheduleId, long maxActive, long batchSize, long activeTimeMs) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        long now = System.currentTimeMillis();
        long expireAt = now + activeTimeMs;

        List<String> movedMembers = redisTemplate.execute(
                activeMemberScript,
                List.of(waitKey, activeKey),
                String.valueOf(maxActive),
                String.valueOf(batchSize),
                String.valueOf(expireAt),
                String.valueOf(now)
        );

        cleanupProcessingScheduleIfDrained(scheduleId);
        return movedMembers;
    }

    public void removeActiveMember(Long scheduleId, Long memberId) {
        redisTemplate.opsForZSet().remove(getActiveKey(scheduleId), memberId.toString());
        cleanupProcessingScheduleIfDrained(scheduleId);
    }

    public void removeWaitingMember(Long scheduleId, Long memberId) {
        redisTemplate.opsForZSet().remove(getWaitKey(scheduleId), memberId.toString());
        cleanupProcessingScheduleIfDrained(scheduleId);
    }

    public void addProcessingSchedule(Long scheduleId) {
        redisTemplate.opsForSet().add(PROCESSING_SCHEDULES_KEY, scheduleId.toString());
    }

    public Set<Long> getProcessingSchedules() {
        Set<String> members = redisTemplate.opsForSet().members(PROCESSING_SCHEDULES_KEY);

        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    public void removeProcessingSchedule(Long scheduleId) {
        redisTemplate.opsForSet().remove(PROCESSING_SCHEDULES_KEY, scheduleId.toString());
    }

    private void cleanupProcessingScheduleIfDrained(Long scheduleId) {
        redisTemplate.execute(
                cleanupProcessingScheduleScript,
                List.of(getWaitKey(scheduleId), getActiveKey(scheduleId), PROCESSING_SCHEDULES_KEY),
                scheduleId.toString()
        );
    }

    private String getWaitKey(Long scheduleId) {
        return "queue:{" + scheduleId + "}:wait";
    }

    private String getActiveKey(Long scheduleId) {
        return "queue:{" + scheduleId + "}:active";
    }

    private String getScoreKey(Long scheduleId) {
        return "queue:{" + scheduleId + "}:score";
    }

    private String getMetaKey(Long scheduleId) {
        return "queue:{" + scheduleId + "}:meta";
    }
}
