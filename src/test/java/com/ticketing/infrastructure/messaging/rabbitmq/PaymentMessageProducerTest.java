package com.ticketing.infrastructure.messaging.rabbitmq;

import com.ticketing.application.payment.service.PaymentRequestedEvent;
import com.ticketing.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = {
        "spring.session.store-type=none",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "ticketing.payment.rabbitmq.listener-auto-startup=false"
})
class PaymentMessageProducerTest extends IntegrationTestSupport {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("결제 요청 이벤트는 트랜잭션 커밋 후 RabbitMQ로 발행된다.")
    void publishPaymentMessageAfterCommit() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(1L, 2L, 3L);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(rabbitTemplate).convertAndSend(
                eq("ticketing.payment"),
                eq("payment.requested"),
                eq(new PaymentRequestedMessage(1L, 2L, 3L))
        );
    }

    @Test
    @DisplayName("결제 요청 이벤트는 트랜잭션이 롤백되면 RabbitMQ로 발행되지 않는다.")
    void doesNotPublishPaymentMessageWhenRolledBack() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(10L, 20L, 30L);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(rabbitTemplate, never()).convertAndSend(
                eq("ticketing.payment"),
                eq("payment.requested"),
                eq(new PaymentRequestedMessage(10L, 20L, 30L))
        );
    }
}
