package com.ticketing.global.config;

import com.ticketing.application.payment.outbox.PaymentOutboxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@ConditionalOnProperty(name = {"ticketing.scheduler.enabled", "ticketing.payment.outbox.enabled"},
        havingValue = "true", matchIfMissing = true)
public class PaymentOutboxExecutionConfig {
    @Bean
    @DependsOn("paymentOutboxRelay")
    public ThreadPoolTaskExecutor paymentOutboxExecutor(PaymentOutboxProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            @Override
            public void shutdown() {
                super.shutdown();
                // Spring's bounded await alone does not interrupt unfinished tasks.
                getThreadPoolExecutor().shutdownNow();
            }
        };
        executor.setCorePoolSize(properties.getWorkerCount());
        executor.setMaxPoolSize(properties.getWorkerCount());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("payment-outbox-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler paymentOutboxTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("payment-outbox-scheduler-");
        return scheduler;
    }
}
