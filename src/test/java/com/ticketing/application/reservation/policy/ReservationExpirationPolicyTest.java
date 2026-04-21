package com.ticketing.application.reservation.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationExpirationPolicyTest {

    @Test
    @DisplayName("hold duration 설정만큼 예약 만료 시각을 계산한다")
    void calculateExpiredAtAddsConfiguredHoldDuration() {
        ReservationExpirationPolicy policy = new ReservationExpirationPolicy();
        LocalDateTime reservedAt = LocalDateTime.of(2026, 4, 21, 12, 0);

        ReflectionTestUtils.setField(policy, "holdDuration", Duration.ofMinutes(30));

        LocalDateTime expiredAt = policy.calculateExpiredAt(reservedAt);

        assertThat(expiredAt).isEqualTo(reservedAt.plusMinutes(30));
    }
}
