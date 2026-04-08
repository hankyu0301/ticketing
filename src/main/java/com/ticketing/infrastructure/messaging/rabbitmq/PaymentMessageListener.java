package com.ticketing.infrastructure.messaging.rabbitmq;

import com.ticketing.application.payment.service.PaymentProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMessageListener {

    private final PaymentProcessor paymentProcessor;

    @RabbitListener(
            queues = "${ticketing.payment.rabbitmq.queue:ticketing.payment.requested}",
            autoStartup = "${ticketing.payment.rabbitmq.listener-auto-startup:true}"
    )
    public void handlePaymentRequested(PaymentRequestedMessage message) {
        paymentProcessor.complete(message.paymentId());
    }
}
