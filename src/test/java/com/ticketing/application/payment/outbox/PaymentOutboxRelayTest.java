package com.ticketing.application.payment.outbox;

import com.ticketing.domain.payment.model.PaymentOutbox;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxRelayTest {
    @Mock
    private PaymentOutboxStateService stateService;
    @Mock
    private PaymentMessageProducer producer;
    @InjectMocks
    private PaymentOutboxRelay relay;
    private final PaymentOutbox outbox = PaymentOutbox.builder().id(1L)
            .eventId("event").paymentId(10L).attemptId("attempt").claimToken("claim").build();

    @BeforeEach
    void setUp() {
        given(stateService.findExpiredClaims()).willReturn(List.of());
        given(stateService.findReadyCandidates()).willReturn(List.of(outbox));
        given(stateService.tryClaim(1L)).willReturn(Optional.of(outbox));
    }

    @Test
    void recordsConfirmedPublication() throws Exception {
        relay.runOnce();
        verify(producer).publish(outbox);
        verify(stateService).markPublished(outbox);
        verify(stateService, never()).markFailed(any(), anyString());
    }

    @Test
    void schedulesRetryForPublicationFailure() throws Exception {
        doThrow(new IllegalStateException("not confirmed")).when(producer).publish(outbox);
        relay.runOnce();
        verify(stateService).markFailed(outbox, "java.lang.IllegalStateException: not confirmed");
        verify(stateService, never()).markPublished(any());
    }

    @Test
    void leavesSuccessfulPublicationForLeaseRecoveryWhenResultStorageFails() {
        given(stateService.markPublished(outbox)).willThrow(new IllegalStateException("DB unavailable"));
        relay.runOnce();
        verify(stateService, never()).markFailed(any(), anyString());
    }

    @Test
    void skipsCandidateClaimedByAnotherWorker() {
        given(stateService.tryClaim(1L)).willReturn(Optional.empty());
        relay.runOnce();
        verifyNoInteractions(producer);
    }

    @Test
    void restoresInterruptAndLeavesClaimForRecovery() throws Exception {
        doThrow(new InterruptedException()).when(producer).publish(outbox);
        try {
            relay.runOnce();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(stateService, never()).markPublished(any());
            verify(stateService, never()).markFailed(any(), anyString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void recoversExpiredClaimsBeforePublishing() {
        given(stateService.findExpiredClaims()).willReturn(List.of(outbox));
        relay.runOnce();
        verify(stateService).recoverExpiredClaim(outbox);
    }
}
