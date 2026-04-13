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

    public static Payment create(Long reservationId, Long memberId, BigDecimal amount) {
        return Payment.builder()
                .reservationId(reservationId)
                .memberId(memberId)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .paidAt(null)
                .build();
    }

    public void complete(LocalDateTime paidAt) {
        this.status = PaymentStatus.COMPLETED;
        this.paidAt = paidAt;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
        this.paidAt = null;
    }

    public void resetPending() {
        this.status = PaymentStatus.PENDING;
        this.paidAt = null;
    }

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
}
