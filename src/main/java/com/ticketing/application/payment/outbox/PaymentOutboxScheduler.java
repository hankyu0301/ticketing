package com.ticketing.application.payment.outbox;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"ticketing.scheduler.enabled", "ticketing.payment.outbox.enabled"},
        havingValue = "true", matchIfMissing = true)
public class PaymentOutboxScheduler {
    private final ThreadPoolTaskExecutor executor;
    private final PaymentOutboxRelay relay;
    private final PaymentOutboxProperties properties;
    private volatile boolean stopping;

    public PaymentOutboxScheduler(@Qualifier("paymentOutboxExecutor") ThreadPoolTaskExecutor executor,
                                  PaymentOutboxRelay relay, PaymentOutboxProperties properties) {
        this.executor = executor;
        this.relay = relay;
        this.properties = properties;
    }

    @Scheduled(scheduler = "paymentOutboxTaskScheduler",
            fixedDelayString = "${ticketing.payment.outbox.poll-delay-ms:500}")
    public void dispatch() {
        for (int i = 0; i < properties.getWorkerCount() && !stopping; i++) {
            try {
                executor.execute(relay::runOnce);
            } catch (TaskRejectedException e) {
                // No claim has been taken; a later poll can safely retry.
                return;
            }
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopDispatching() {
        stopping = true;
    }
}
