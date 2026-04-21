package com.ticketing.application.reservation.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class ReservationExpirationPolicy {

    @Value("${ticketing.reservation.hold-duration:PT30M}")
    private Duration holdDuration;

    public LocalDateTime calculateExpiredAt(LocalDateTime reservedAt) {
        return reservedAt.plus(holdDuration);
    }
}
