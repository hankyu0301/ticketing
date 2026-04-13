package com.ticketing.infrastructure.messaging.rabbitmq;

import com.ticketing.application.payment.service.AsyncPaymentProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMessageListener {

    private final AsyncPaymentProcessor asyncPaymentProcessor;

    @RabbitListener(
            queues = "${ticketing.payment.rabbitmq.queue:ticketing.payment.requested}",
            autoStartup = "${ticketing.payment.rabbitmq.listener-auto-startup:true}"
    )
    public void handlePaymentRequested(PaymentRequestedMessage message) {
        asyncPaymentProcessor.complete(message.paymentId());
    }
}
