package com.ticketing.application.payment.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOutboxRetryPolicyTest {
    private final PaymentOutboxProperties properties = new PaymentOutboxProperties();
    private final PaymentOutboxRetryPolicy policy = new PaymentOutboxRetryPolicy(properties);

    @Test
    void increasesRetryDelayWithBoundedJitter() {
        assertThat(policy.nextDelayMillis(1)).isBetween(800L, 1_000L);
        assertThat(policy.nextDelayMillis(2)).isBetween(1_600L, 2_000L);
        assertThat(policy.nextDelayMillis(6)).isBetween(25_600L, 32_000L);
    }

    @Test
    void capsDelayWithoutOverflowForLargeAttemptCounts() {
        assertThat(policy.nextDelayMillis(Integer.MAX_VALUE)).isBetween(48_000L, 60_000L);
    }
}
