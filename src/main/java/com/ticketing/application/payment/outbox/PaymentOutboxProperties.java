package com.ticketing.application.payment.outbox;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ticketing.payment.outbox")
public class PaymentOutboxProperties {
    private boolean enabled = true;
    @Min(1)
    private long pollDelayMs = 500;
    @Min(1)
    private int workerCount = 4;
    @Min(1)
    private int candidateLimit = 20;
    @Min(1)
    private int recoveryLimit = 20;
    @Min(1)
    private long confirmTimeoutMs = 5_000;
    @Min(1)
    private long leaseDurationMs = 60_000;
    @Min(1)
    private int maxAttempts = 10;
    @Min(1)
    private long retryInitialDelayMs = 1_000;
    @Min(1)
    private long retryMaxDelayMs = 60_000;

    @AssertTrue(message = "lease-duration-ms must exceed confirm-timeout-ms")
    public boolean isLeaseLongerThanConfirmTimeout() {
        return leaseDurationMs > confirmTimeoutMs;
    }

    @AssertTrue(message = "retry-max-delay-ms must be at least retry-initial-delay-ms")
    public boolean isRetryDelayRangeValid() {
        return retryMaxDelayMs >= retryInitialDelayMs;
    }
}
