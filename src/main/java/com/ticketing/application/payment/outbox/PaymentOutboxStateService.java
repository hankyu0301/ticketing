package com.ticketing.application.payment.outbox;

import com.ticketing.domain.payment.model.PaymentOutbox;
import com.ticketing.infrastructure.persistence.payment.PaymentOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxStateService {
    private final PaymentOutboxMapper outboxMapper;
    private final PaymentOutboxProperties properties;
    private final PaymentOutboxRetryPolicy retryPolicy;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<PaymentOutbox> findReadyCandidates() {
        return outboxMapper.findReadyCandidates(properties.getCandidateLimit(), properties.getMaxAttempts());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<PaymentOutbox> findExpiredClaims() {
        return outboxMapper.findExpiredClaims(properties.getRecoveryLimit());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentOutbox> tryClaim(Long id) {
        String token = UUID.randomUUID().toString();
        if (outboxMapper.tryClaim(id, token, Math.multiplyExact(properties.getLeaseDurationMs(), 1_000),
                properties.getMaxAttempts()) == 0) {
            return Optional.empty();
        }
        return Optional.of(outboxMapper.findById(id).orElseThrow());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(PaymentOutbox outbox) {
        return outboxMapper.markPublished(outbox.getId(), outbox.getClaimToken()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(PaymentOutbox outbox, String error) {
        return release(outbox, error, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverExpiredClaim(PaymentOutbox outbox) {
        return release(outbox, "CLAIM_EXPIRED", true);
    }

    private boolean release(PaymentOutbox outbox, String error, boolean expiredOnly) {
        boolean parked = outbox.getAttemptCount() >= properties.getMaxAttempts();
        long delayMicros = parked ? 0 : Math.multiplyExact(retryPolicy.nextDelayMillis(outbox.getAttemptCount()), 1_000);
        String boundedError = error == null ? "Unknown publication failure" : error.substring(0, Math.min(2_000, error.length()));
        boolean changed = outboxMapper.markFailed(outbox.getId(), outbox.getClaimToken(), parked,
                delayMicros, boundedError, expiredOnly) == 1;
        if (changed && parked) {
            log.error("Payment outbox parked. eventId={}, paymentId={}, attemptId={}, attempts={}, error={}",
                    outbox.getEventId(), outbox.getPaymentId(), outbox.getAttemptId(),
                    outbox.getAttemptCount(), boundedError);
        }
        return changed;
    }
}
