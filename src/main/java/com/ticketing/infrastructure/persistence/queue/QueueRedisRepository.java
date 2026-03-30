package com.ticketing.infrastructure.persistence.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final Set<Long> localCache = ConcurrentHashMap.newKeySet();

    private final RedisScript<Long> queueEnterScript;
    private final RedisScript<Long> queueStatusScript;
    private final RedisScript<List> activeMemberScript;
    private final RedisScript<Long> validExtensionScript;

    private static final String PROCESSING_SCHEDULES_KEY = "queue:processing_schedules";

    @Value("${queue.safety-ttl-days:7}")
    private long safetyTtlDays;

    public Long register(Long scheduleId, Long memberId, long maxActive, long activeTimeMs) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        String scoreKey = getScoreKey(scheduleId);
        long now = System.currentTimeMillis();
        long expireAt = now + activeTimeMs;
        long ttlSeconds = safetyTtlDays * 24 * 60 * 60;

        return redisTemplate.execute(
                queueEnterScript,
                List.of(waitKey, activeKey, scoreKey),
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

        return redisTemplate.execute(
                queueStatusScript,
                List.of(waitKey, activeKey),
                memberId.toString(),
                String.valueOf(System.currentTimeMillis())
        );
    }

    public boolean validateAndExtendActiveMember(Long scheduleId, Long memberId) {
        String activeKey = getActiveKey(scheduleId);
        long currentTime = System.currentTimeMillis();
        long newExpireAt = currentTime + (5 * 60 * 1000);

        Long result = redisTemplate.execute(
                validExtensionScript,
                List.of(activeKey),
                memberId.toString(),
                String.valueOf(currentTime),
                String.valueOf(newExpireAt)
        );

        return result != null && result == 1L;
    }

    public List<String> moveUsersToActive(Long scheduleId, long maxActive, long batchSize, long activeTimeMs) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        long now = System.currentTimeMillis();
        long expireAt = now + activeTimeMs;

        return redisTemplate.execute(
                activeMemberScript,
                List.of(waitKey, activeKey),
                String.valueOf(maxActive),
                String.valueOf(batchSize),
                String.valueOf(expireAt),
                String.valueOf(now)
        );
    }

    public void removeActiveMember(Long scheduleId, Long memberId) {
        redisTemplate.opsForZSet().remove(getActiveKey(scheduleId), memberId.toString());
    }

    public void removeWaitingMember(Long scheduleId, Long memberId) {
        redisTemplate.opsForZSet().remove(getWaitKey(scheduleId), memberId.toString());
    }

    public void addProcessingSchedule(Long scheduleId) {
        if (localCache.contains(scheduleId)) {
            return;
        }

        redisTemplate.opsForSet().add(PROCESSING_SCHEDULES_KEY, scheduleId.toString());
        localCache.add(scheduleId);
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
        localCache.remove(scheduleId);
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
}
