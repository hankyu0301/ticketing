package com.ticketing.application.payment.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class PaymentOutboxRetryPolicy {
    private final PaymentOutboxProperties properties;

    public long nextDelayMillis(int attemptCount) {
        long delay = properties.getRetryInitialDelayMs();
        long maximum = properties.getRetryMaxDelayMs();
        for (int i = 1; i < attemptCount && delay < maximum; i++) {
            delay = delay > maximum / 2 ? maximum : Math.min(maximum, delay * 2);
        }
        return Math.max(1, (long) (delay * ThreadLocalRandom.current().nextDouble(0.8, 1.0)));
    }
}
