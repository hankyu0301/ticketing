package com.ticketing.infrastructure.messaging.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.application.payment.outbox.PaymentOutboxProperties;
import com.ticketing.domain.payment.model.PaymentOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class PaymentMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentOutboxProperties properties;

    @Value("${ticketing.payment.rabbitmq.exchange:ticketing.payment}")
    private String exchangeName;

    @Value("${ticketing.payment.rabbitmq.routing-key:payment.requested}")
    private String routingKey;

    public void publish(PaymentOutbox outbox) throws InterruptedException {
        PaymentRequestedMessage message = readMessage(outbox);
        CorrelationData correlation = new CorrelationData(outbox.getEventId() + ":" + outbox.getClaimToken());
        rabbitTemplate.convertAndSend(exchangeName, routingKey, message, amqpMessage -> {
            amqpMessage.getMessageProperties().setMessageId(outbox.getEventId());
            amqpMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return amqpMessage;
        }, correlation);

        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected publication: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned publication: " + correlation.getReturned().getReplyText());
            }
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("RabbitMQ publication was not confirmed", e);
        }
    }

    private PaymentRequestedMessage readMessage(PaymentOutbox outbox) {
        try {
            PaymentRequestedMessage message = objectMapper.readValue(outbox.getPayload(), PaymentRequestedMessage.class);
            if (!"PAYMENT_REQUESTED".equals(outbox.getEventType()) || outbox.getSchemaVersion() != 1
                    || message.schemaVersion() != 1
                    || !Objects.equals(message.eventId(), outbox.getEventId())
                    || !Objects.equals(message.attemptId(), outbox.getAttemptId())
                    || !Objects.equals(message.paymentId(), outbox.getPaymentId())) {
                throw new IllegalStateException("Invalid payment outbox envelope: " + outbox.getEventId());
            }
            return message;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read payment outbox payload: " + outbox.getEventId(), e);
        }
    }
}
