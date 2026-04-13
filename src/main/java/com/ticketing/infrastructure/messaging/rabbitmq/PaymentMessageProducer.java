package com.ticketing.infrastructure.messaging.rabbitmq;

import com.ticketing.application.payment.service.PaymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${ticketing.payment.rabbitmq.exchange:ticketing.payment}")
    private String exchangeName;

    @Value("${ticketing.payment.rabbitmq.routing-key:payment.requested}")
    private String routingKey;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentRequested(PaymentRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                exchangeName,
                routingKey,
                new PaymentRequestedMessage(event.paymentId(), event.reservationId(), event.memberId())
        );
    }
}
