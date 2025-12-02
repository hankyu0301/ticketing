package com.ticketing.domain.payment.model;

import com.ticketing.global.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends BaseEntity {
    private Long paymentId;
    private Long reservationId;
    private Long memberId;
    private BigDecimal amount;

    private PaymentStatus status;
    private LocalDateTime paidAt;

    public enum PaymentStatus {
        COMPLETED, FAILED, REFUNDED
    }
}