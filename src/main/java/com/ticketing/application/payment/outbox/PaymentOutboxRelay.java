package com.ticketing.application.payment.outbox;

import com.ticketing.domain.payment.model.PaymentOutbox;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxRelay {
    private final PaymentOutboxStateService stateService;
    private final PaymentMessageProducer producer;

    public void runOnce() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            for (PaymentOutbox expired : stateService.findExpiredClaims()) {
                stateService.recoverExpiredClaim(expired);
            }
            for (PaymentOutbox candidate : stateService.findReadyCandidates()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                Optional<PaymentOutbox> claimed = stateService.tryClaim(candidate.getId());
                if (claimed.isPresent()) {
                    publish(claimed.get());
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.error("Payment outbox worker failed; unfinished claims will expire", e);
        }
    }

    private void publish(PaymentOutbox outbox) {
        try {
            producer.publish(outbox);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Payment outbox worker interrupted. eventId={}, paymentId={}, attemptId={}",
                    outbox.getEventId(), outbox.getPaymentId(), outbox.getAttemptId());
            return;
        } catch (RuntimeException e) {
            log.warn("Payment outbox publication failed. eventId={}, paymentId={}, attemptId={}, error={}",
                    outbox.getEventId(), outbox.getPaymentId(), outbox.getAttemptId(), e.toString());
            stateService.markFailed(outbox, e.toString());
            return;
        }

        // A DB failure here must not be mistaken for a failed RabbitMQ publication.
        // Leave the claim for lease recovery and tolerate a duplicate delivery.
        if (!stateService.markPublished(outbox)) {
            log.debug("Payment outbox claim was replaced. eventId={}", outbox.getEventId());
        }
    }
}
