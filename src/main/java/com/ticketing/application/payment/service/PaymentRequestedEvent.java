package com.ticketing.application.payment.service;

public record PaymentRequestedEvent(String eventId, String attemptId, int schemaVersion,
                                    Long paymentId, Long reservationId, Long memberId) {
}
