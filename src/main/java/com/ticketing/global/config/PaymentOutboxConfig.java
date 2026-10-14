package com.ticketing.global.config;

import com.ticketing.application.payment.outbox.PaymentOutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentOutboxProperties.class)
public class PaymentOutboxConfig {
}
