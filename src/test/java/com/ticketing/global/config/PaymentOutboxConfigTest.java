package com.ticketing.global.config;

import com.ticketing.application.payment.outbox.PaymentOutboxProperties;
import com.ticketing.application.payment.outbox.PaymentOutboxRelay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentOutboxConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean("paymentOutboxRelay", PaymentOutboxRelay.class, () -> mock(PaymentOutboxRelay.class))
            .withUserConfiguration(PaymentOutboxConfig.class, PaymentOutboxExecutionConfig.class);

    @Test
    void createsDedicatedExecutionResourcesByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasBean("paymentOutboxExecutor").hasBean("paymentOutboxTaskScheduler");
            assertThat(context.getBean(PaymentOutboxProperties.class).getWorkerCount()).isEqualTo(4);
        });
    }

    @Test
    void disablingOutboxKeepsPropertiesButDoesNotCreateWorkers() {
        runner.withPropertyValues("ticketing.payment.outbox.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean("paymentOutboxExecutor");
            assertThat(context).hasSingleBean(PaymentOutboxProperties.class);
        });
    }

    @Test
    void globalSchedulerSwitchAlsoDisablesOutboxWorkers() {
        runner.withPropertyValues("ticketing.scheduler.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean("paymentOutboxExecutor"));
    }

    @Test
    void rejectsLeaseShorterThanConfirmationWait() {
        runner.withPropertyValues("ticketing.payment.outbox.lease-duration-ms=1000").run(context ->
                assertThat(context).hasFailed());
    }
}
