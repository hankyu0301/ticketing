package com.ticketing.application.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.application.payment.service.PaymentRequestedEvent;
import com.ticketing.domain.payment.model.PaymentOutbox;
import com.ticketing.infrastructure.persistence.payment.PaymentOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentOutboxWriter {
    private final PaymentOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(PaymentRequestedEvent event) {
        try {
            outboxMapper.save(PaymentOutbox.builder()
                    .eventId(event.eventId())
                    .paymentId(event.paymentId())
                    .attemptId(event.attemptId())
                    .eventType("PAYMENT_REQUESTED")
                    .schemaVersion(event.schemaVersion())
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize payment request " + event.eventId(), e);
        }
    }
}
