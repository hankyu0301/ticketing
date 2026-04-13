package com.ticketing.infrastructure.messaging.rabbitmq;

public record PaymentRequestedMessage(Long paymentId, Long reservationId, Long memberId) {
}
