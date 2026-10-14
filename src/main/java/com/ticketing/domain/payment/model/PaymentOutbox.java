package com.ticketing.domain.payment.model;

import com.ticketing.global.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOutbox extends BaseEntity {
    private Long id;
    private String eventId;
    private Long paymentId;
    private String attemptId;
    private String eventType;
    private int schemaVersion;
    private String payload;
    private Status status;
    private int attemptCount;
    private LocalDateTime availableAt;
    private String claimToken;
    private LocalDateTime lockedUntil;
    private String lastError;
    private LocalDateTime publishedAt;

    public enum Status {
        READY, PROCESSING, PUBLISHED, PARKED
    }
}
