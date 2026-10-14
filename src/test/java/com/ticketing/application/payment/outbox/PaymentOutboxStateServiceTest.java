package com.ticketing.application.payment.outbox;

import com.ticketing.domain.payment.model.PaymentOutbox;
import com.ticketing.infrastructure.persistence.payment.PaymentOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentOutboxStateServiceTest {
    private final PaymentOutboxMapper mapper = mock(PaymentOutboxMapper.class);
    private final PaymentOutboxProperties properties = new PaymentOutboxProperties();
    private final PaymentOutboxStateService service = new PaymentOutboxStateService(mapper, properties,
            new PaymentOutboxRetryPolicy(properties));

    @BeforeEach
    void setUp() {
        given(mapper.markFailed(eq(1L), eq("claim"), anyBoolean(), anyLong(), anyString(), anyBoolean()))
                .willReturn(1);
    }

    @Test
    void parksAtAttemptLimitAndBoundsErrorLength() {
        PaymentOutbox outbox = PaymentOutbox.builder().id(1L).claimToken("claim").attemptCount(10).build();
        assertThat(service.markFailed(outbox, "x".repeat(3_000))).isTrue();
        verify(mapper).markFailed(1L, "claim", true, 0, "x".repeat(2_000), false);
    }

    @Test
    void recoveryRetainsAttemptCountAndRequiresExpiredLease() {
        PaymentOutbox outbox = PaymentOutbox.builder().id(1L).claimToken("claim").attemptCount(10).build();
        assertThat(service.recoverExpiredClaim(outbox)).isTrue();
        verify(mapper).markFailed(1L, "claim", true, 0, "CLAIM_EXPIRED", true);
    }
}
