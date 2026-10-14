package com.ticketing.infrastructure.messaging.rabbitmq;

public record PaymentRequestedMessage(String eventId, String attemptId, int schemaVersion,
                                      Long paymentId, Long reservationId, Long memberId) {
}
