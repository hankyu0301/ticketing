package com.ticketing.infrastructure.persistence.seat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SeatUnavailableCacheRepository {

    private static final String TEMPORARY = "TEMPORARY";
    private static final String SOLD_OUT = "SOLD_OUT";

    private final RedisTemplate<String, String> redisTemplate;

    public boolean isUnavailable(Long scheduleId, Long seatId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getSeatKey(scheduleId, seatId)));
        } catch (RuntimeException e) {
            log.warn("좌석 unavailable cache 조회 실패. scheduleId={}, seatId={}", scheduleId, seatId, e);
            return false;
        }
    }

    public void markTemporary(Long scheduleId, Long seatId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(getSeatKey(scheduleId, seatId), TEMPORARY, ttl);
            redisTemplate.opsForSet().add(getSeatSetKey(scheduleId), seatId.toString());
        } catch (RuntimeException e) {
            log.warn("좌석 TEMPORARY unavailable cache 저장 실패. scheduleId={}, seatId={}", scheduleId, seatId, e);
        }
    }

    public void markSoldOut(Long scheduleId, Long seatId) {
        try {
            redisTemplate.opsForValue().set(getSeatKey(scheduleId, seatId), SOLD_OUT);
            redisTemplate.opsForSet().add(getSeatSetKey(scheduleId), seatId.toString());
        } catch (RuntimeException e) {
            log.warn("좌석 SOLD_OUT unavailable cache 저장 실패. scheduleId={}, seatId={}", scheduleId, seatId, e);
        }
    }

    public void evict(Long scheduleId, Long seatId) {
        try {
            redisTemplate.delete(getSeatKey(scheduleId, seatId));
            redisTemplate.opsForSet().remove(getSeatSetKey(scheduleId), seatId.toString());
        } catch (RuntimeException e) {
            log.warn("좌석 unavailable cache 삭제 실패. scheduleId={}, seatId={}", scheduleId, seatId, e);
        }
    }

    private String getSeatKey(Long scheduleId, Long seatId) {
        return "seat:unavailable:{" + scheduleId + "}:" + seatId;
    }

    private String getSeatSetKey(Long scheduleId) {
        return "seat:unavailable:{" + scheduleId + "}:ids";
    }
}
