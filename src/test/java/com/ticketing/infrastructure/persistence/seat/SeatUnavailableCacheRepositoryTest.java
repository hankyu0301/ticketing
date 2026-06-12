package com.ticketing.infrastructure.persistence.seat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatUnavailableCacheRepositoryTest {

    private static final String SEAT_KEY = "seat:unavailable:{1}:10";
    private static final String SEAT_SET_KEY = "seat:unavailable:{1}:ids";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private SeatUnavailableCacheRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SeatUnavailableCacheRepository(redisTemplate);
    }

    @Test
    @DisplayName("좌석 unavailable cache key가 있으면 사용할 수 없는 좌석으로 판단한다")
    void isUnavailable_returnsTrue_whenSeatKeyExists() {
        given(redisTemplate.hasKey(SEAT_KEY)).willReturn(true);

        boolean unavailable = repository.isUnavailable(1L, 10L);

        assertThat(unavailable).isTrue();
    }

    @Test
    @DisplayName("임시 점유 좌석은 TTL과 함께 캐시에 저장하고 schedule별 set에 등록한다")
    void markTemporary_savesSeatKeyAndSetKeyWithTtl() {
        Duration ttl = Duration.ofMinutes(31);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        repository.markTemporary(1L, 10L, ttl);

        verify(valueOperations).set(SEAT_KEY, "TEMPORARY", ttl);
        verify(setOperations).add(SEAT_SET_KEY, "10");
    }

    @Test
    @DisplayName("결제 완료 좌석은 SOLD_OUT 상태로 캐시에 저장한다")
    void markSoldOut_savesSoldOutSeatKey() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        repository.markSoldOut(1L, 10L);

        verify(valueOperations).set(SEAT_KEY, "SOLD_OUT");
        verify(setOperations).add(SEAT_SET_KEY, "10");
    }

    @Test
    @DisplayName("예약 만료 좌석은 unavailable cache에서 제거한다")
    void evict_deletesSeatKeyAndRemovesSeatIdFromSet() {
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        repository.evict(1L, 10L);

        verify(redisTemplate).delete(SEAT_KEY);
        verify(setOperations).remove(SEAT_SET_KEY, "10");
    }
}
