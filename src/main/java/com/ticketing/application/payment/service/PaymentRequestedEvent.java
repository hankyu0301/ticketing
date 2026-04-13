package com.ticketing.application.payment.service;

public record PaymentRequestedEvent(Long paymentId, Long reservationId, Long memberId) {
}
