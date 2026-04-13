package com.ticketing.api.payment.dto;

import com.ticketing.domain.payment.model.Payment;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PaymentResponse {

    private Long paymentId;
    private Long reservationId;
    private Long memberId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime paidAt;

    public PaymentResponse(Payment payment) {
        this.paymentId = payment.getPaymentId();
        this.reservationId = payment.getReservationId();
        this.memberId = payment.getMemberId();
        this.amount = payment.getAmount();
        this.status = payment.getStatus().name();
        this.paidAt = payment.getPaidAt();
    }
}
