package com.ticketing.infrastructure.messaging.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.application.payment.outbox.PaymentOutboxProperties;
import com.ticketing.domain.payment.model.PaymentOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentMessageProducerTest {
    @Mock
    private RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentOutboxProperties properties = new PaymentOutboxProperties();
    private PaymentMessageProducer producer;
    private PaymentOutbox outbox;

    @BeforeEach
    void setUp() throws Exception {
        producer = new PaymentMessageProducer(rabbitTemplate, objectMapper, properties);
        ReflectionTestUtils.setField(producer, "exchangeName", "ticketing.payment");
        ReflectionTestUtils.setField(producer, "routingKey", "payment.requested");
        outbox = PaymentOutbox.builder().id(1L).eventId("event").attemptId("attempt")
                .paymentId(10L).eventType("PAYMENT_REQUESTED").schemaVersion(1).claimToken("claim")
                .payload(objectMapper.writeValueAsString(new PaymentRequestedMessage("event", "attempt", 1, 10L, 20L, 30L)))
                .build();
    }

    @Test
    void acceptsAckAndSetsPersistentMessageId() throws Exception {
        confirm(true, false);
        producer.publish(outbox);
    }

    @Test
    void rejectsNack() {
        confirm(false, false);
        assertThatThrownBy(() -> producer.publish(outbox)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void rejectsReturnedMessageEvenWithAck() {
        confirm(true, true);
        assertThatThrownBy(() -> producer.publish(outbox)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned");
    }

    @Test
    void timesOutWithoutConfirmation() {
        properties.setConfirmTimeoutMs(1);
        assertThatThrownBy(() -> producer.publish(outbox)).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void propagatesInterruptedWait() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> producer.publish(outbox)).isInstanceOf(InterruptedException.class);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void propagatesSendFailure() {
        doThrow(new IllegalStateException("connection failed")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThatThrownBy(() -> producer.publish(outbox)).hasMessage("connection failed");
    }

    @Test
    void rejectsPayloadFromAnotherAttempt() {
        ReflectionTestUtils.setField(outbox, "attemptId", "another-attempt");
        assertThatThrownBy(() -> producer.publish(outbox)).hasMessageContaining("Invalid");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void rejectsMalformedPayload() {
        ReflectionTestUtils.setField(outbox, "payload", "invalid json");
        assertThatThrownBy(() -> producer.publish(outbox)).hasMessageContaining("Cannot read");
        verifyNoInteractions(rabbitTemplate);
    }

    private void confirm(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            MessagePostProcessor processor = invocation.getArgument(3);
            Message message = processor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo("event");
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            CorrelationData correlation = invocation.getArgument(4);
            assertThat(correlation.getId()).isEqualTo("event:claim");
            if (returned) {
                correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", "ticketing.payment", "payment.requested"));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "failed"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq("ticketing.payment"), eq("payment.requested"),
                any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }
}
