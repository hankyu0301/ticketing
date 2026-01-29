package com.ticketing.domain.queue.repository;

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
    private Set<Long> localCache = new ConcurrentHashMap().newKeySet();

    private final RedisScript<Long> queueEnterScript;
    private final RedisScript<Long> queueStatusScript;
    private final RedisScript<List> activeMemberScript;

    private static final String PROCESSING_SCHEDULES_KEY = "queue:processing_schedules";

    @Value("${queue.safety-ttl-days:7}")
    private long safetyTtlDays;

    /**
     * 대기열 등록 (Lua Script)
     * - ZADD NX 옵션을 사용하여 최초 진입 시간(Score) 유지
     * @return 현재 대기 순번 (0부터 시작), 오류 시 -1
     */
    public Long register(Long scheduleId, Long memberId, long maxActive, long activeTimeMs) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        String scoreKey = getScoreKey(scheduleId);
        long now = System.currentTimeMillis();
        long expireAt = now + activeTimeMs;
        long ttlSeconds = safetyTtlDays * 24 * 60 * 60;

        return redisTemplate.execute(
                queueEnterScript,
                List.of(waitKey, activeKey, scoreKey),    // KEYS[1], KEYS[2], KEYS[3]
                memberId.toString(),            // ARGV[1]
                String.valueOf(maxActive),      // ARGV[2] (최대 인원)
                String.valueOf(expireAt),       // ARGV[3] (만료 시간)
                String.valueOf(ttlSeconds)      // ARGV[4] (만료 시간)
        );
    }

    /**
     * 상태 조회 (Lua Script)
     * - 활성열(Active)에 있는지 먼저 확인하고, 없으면 대기열(Wait) 순번 조회
     * @return -100(입장완료), 0 이상(대기 순번), -1(대기열/활성열 모두 없음)
     */
    public Long getRank(Long scheduleId, Long memberId) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);

        return redisTemplate.execute(
                queueStatusScript,
                List.of(waitKey, activeKey),      // KEYS[1], KEYS[2]
                memberId.toString()               // ARGV[1]
        );
    }

    /**
     * 활성 유저 검증 및 시간 연장
     * @return true(유효함), false(만료됨/없음)
     */
    public boolean validateAndExtendActiveMember(Long scheduleId, Long memberId) {
        String activeKey = getActiveKey(scheduleId);

        // 1. 점수(만료시간) 조회 -> 존재 여부 확인 (O(1))
        Double score = redisTemplate.opsForZSet().score(activeKey, memberId.toString());

        if (score == null) {
            return false; // 활성열에 없음
        }

        // 2. 존재한다면 시간 연장 (O(logN))
        long newExpireAt = System.currentTimeMillis() + (5 * 60 * 1000); // 5분 연장
        redisTemplate.opsForZSet().add(activeKey, memberId.toString(), newExpireAt);

        return true;
    }

    /**
     * 대기열 유저를 활성열로 이동 (스케줄러용 통합 메서드)
     * 1. 만료 청소
     * 2. 가용 인원 계산
     * 3. 이동 (Wait -> Active)
     * @return 입장 성공한 MemberId 목록
     */
    public List<String> moveUsersToActive(Long scheduleId, long maxActive, long batchSize, long activeTimeMs) {
        String waitKey = getWaitKey(scheduleId);
        String activeKey = getActiveKey(scheduleId);
        String scoreKey = getScoreKey(scheduleId);

        long now = System.currentTimeMillis();
        long expireAt = now + activeTimeMs;

        // execute 결과는 List<String> 등으로 캐스팅됨
        return redisTemplate.execute(
                activeMemberScript,
                List.of(waitKey, activeKey, scoreKey), // KEYS
                String.valueOf(maxActive),   // ARGV[1]
                String.valueOf(batchSize),   // ARGV[2]
                String.valueOf(expireAt),    // ARGV[3]
                String.valueOf(now)          // ARGV[4]
        );
    }

    /**
     * 활성열에서 특정 멤버 제거 (로그아웃/작업완료 시)
     */
    public void removeActiveMember(Long scheduleId, Long memberId) {
        String key = getActiveKey(scheduleId);
        redisTemplate.opsForZSet().remove(key, memberId.toString());
    }

    /**
     * 대기열에서 특정 멤버 제거 (대기열 이탈시)
     */
    public void removeWaitingMember(Long scheduleId, Long memberId) {
        String key = getWaitKey(scheduleId);
        redisTemplate.opsForZSet().remove(key, memberId.toString());
    }

    /**
     * 관리 대상 스케줄 추가 (SADD)
     * - 대기열이 발생하는 스케줄 ID를 Set에 저장
     */
    public void addProcessingSchedule(Long scheduleId) {
        if (localCache.contains(scheduleId)) {
            return;
        }

        redisTemplate.opsForSet().add(PROCESSING_SCHEDULES_KEY, scheduleId.toString());
        localCache.add(scheduleId);
    }

    /**
     * 관리 대상 스케줄 목록 조회 (SMEMBERS)
     * - 스케줄러가 작업할 대상 목록 반환
     */
    public Set<Long> getProcessingSchedules() {
        Set<String> members = redisTemplate.opsForSet().members(PROCESSING_SCHEDULES_KEY);

        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    /**
     * 관리 대상 스케줄 제거 (SREM)
     * - 더 이상 관리할 필요가 없을 때(공연 종료 등) 호출
     */
    public void removeProcessingSchedule(Long scheduleId) {
        redisTemplate.opsForSet().remove(PROCESSING_SCHEDULES_KEY, scheduleId.toString());
        localCache.remove(scheduleId);
    }

    // ====================================================
    // Helper Methods
    // ====================================================
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