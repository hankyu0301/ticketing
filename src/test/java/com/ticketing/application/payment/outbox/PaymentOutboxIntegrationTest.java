package com.ticketing.application.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.application.payment.service.PaymentService;
import com.ticketing.application.reservation.facade.ReservationFacade;
import com.ticketing.application.reservation.service.ReservationExpirationService;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.payment.model.PaymentOutbox;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentMessageListener;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentMessageProducer;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentRequestedMessage;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.payment.PaymentOutboxMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentOutboxIntegrationTest extends IntegrationTestSupport {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ReservationFacade reservationFacade;
    @Autowired
    private TicketingTestDataSetup dataSetup;
    @Autowired
    private DatabaseCleaner databaseCleaner;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PaymentOutboxMapper outboxMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private PaymentOutboxRelay relay;
    @Autowired
    private PaymentMessageListener listener;
    @Autowired
    private ReservationExpirationService expirationService;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private PaymentOutboxStateService stateService;
    @MockitoBean
    private PaymentMessageProducer producer;
    private PaymentOutbox outbox;

    @BeforeEach
    void setUp() {
        databaseCleaner.execute();
        TicketingTestDataSetup.TestData data = dataSetup.setupConcertAndSeat();
        Long memberId = dataSetup.setupMembers(1).get(0);
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", data.scheduleId());
        ReflectionTestUtils.setField(request, "seatId", data.seatId());
        Long reservationId = reservationFacade.reserve(memberId, request).getReservationId();
        paymentService.requestAsync(memberId, reservationId);
        outbox = stateService.findReadyCandidates().get(0);
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.execute();
    }

    @Test
    void onlyOneConcurrentWorkerCanClaimTheSameEvent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<PaymentOutbox>>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return stateService.tryClaim(outbox.getId());
                }));
            }
            start.countDown();
            int successfulClaims = 0;
            for (Future<Optional<PaymentOutbox>> result : results) {
                if (result.get(15, TimeUnit.SECONDS).isPresent()) {
                    successfulClaims++;
                }
            }
            assertThat(successfulClaims).isEqualTo(1);
            assertThat(reload().getAttemptCount()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredClaimCanBeReplacedButOldTokenCannotUpdateIt() {
        PaymentOutbox original = stateService.tryClaim(outbox.getId()).orElseThrow();
        assertThat(stateService.recoverExpiredClaim(original)).isFalse();
        expireClaim();
        assertThat(stateService.recoverExpiredClaim(original)).isTrue();
        assertThat(reload().getAttemptCount()).isEqualTo(1);
        makeReadyNow();
        PaymentOutbox replacement = stateService.tryClaim(outbox.getId()).orElseThrow();

        assertThat(replacement.getClaimToken()).isNotEqualTo(original.getClaimToken());
        assertThat(stateService.markPublished(original)).isFalse();
        assertThat(stateService.markFailed(original, "late failure")).isFalse();
        assertThat(stateService.recoverExpiredClaim(original)).isFalse();
        assertThat(reload().getClaimToken()).isEqualTo(replacement.getClaimToken());
        assertThat(stateService.markPublished(replacement)).isTrue();
    }

    @Test
    void parksAfterTenthFailureAndDoesNotChangePaymentStatus() {
        jdbcTemplate.update("UPDATE payment_outbox SET attempt_count = 9 WHERE id = ?", outbox.getId());
        PaymentOutbox claimed = stateService.tryClaim(outbox.getId()).orElseThrow();
        assertThat(claimed.getAttemptCount()).isEqualTo(10);
        stateService.markFailed(claimed, "broker unavailable");

        assertThat(reload().getStatus()).isEqualTo(PaymentOutbox.Status.PARKED);
        assertThat(stateService.findReadyCandidates()).isEmpty();
        assertThat(paymentMapper.findById(outbox.getPaymentId()).orElseThrow().getStatus())
                .isEqualTo(Payment.PaymentStatus.PENDING);
    }

    @Test
    void parksExpiredTenthClaimWithoutResettingAttempts() {
        jdbcTemplate.update("UPDATE payment_outbox SET attempt_count = 9 WHERE id = ?", outbox.getId());
        PaymentOutbox claimed = stateService.tryClaim(outbox.getId()).orElseThrow();
        expireClaim();
        stateService.recoverExpiredClaim(claimed);
        assertThat(reload().getStatus()).isEqualTo(PaymentOutbox.Status.PARKED);
        assertThat(reload().getAttemptCount()).isEqualTo(10);
    }

    @Test
    void schedulesRetryInTheFutureAndKeepsPayloadUnchanged() {
        PaymentOutbox claimed = stateService.tryClaim(outbox.getId()).orElseThrow();
        stateService.markFailed(claimed, "not confirmed");
        PaymentOutbox ready = reload();
        assertThat(ready.getStatus()).isEqualTo(PaymentOutbox.Status.READY);
        assertThat(ready.getAvailableAt()).isAfter(ready.getUpdatedAt());
        assertThat(ready.getPayload()).isEqualTo(outbox.getPayload());
        assertThat(ready.getClaimToken()).isNull();
        assertThat(stateService.findReadyCandidates()).isEmpty();
    }

    @Test
    void resultStorageFailureCausesSafeRedeliveryOutsideDatabaseTransaction() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            PaymentOutbox claimed = invocation.getArgument(0);
            listener.handlePaymentRequested(objectMapper.readValue(claimed.getPayload(), PaymentRequestedMessage.class));
            return null;
        }).when(producer).publish(any(PaymentOutbox.class));
        doThrow(new IllegalStateException("result storage unavailable"))
                .doCallRealMethod().when(stateService).markPublished(any(PaymentOutbox.class));

        relay.runOnce();
        assertThat(reload().getStatus()).isEqualTo(PaymentOutbox.Status.PROCESSING);
        Payment completed = paymentMapper.findById(outbox.getPaymentId()).orElseThrow();
        LocalDateTime paidAt = completed.getPaidAt();
        assertThat(completed.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);

        expireClaim();
        relay.runOnce();
        assertThat(reload().getStatus()).isEqualTo(PaymentOutbox.Status.READY);
        makeReadyNow();
        relay.runOnce();

        assertThat(reload().getStatus()).isEqualTo(PaymentOutbox.Status.PUBLISHED);
        assertThat(reload().getAttemptCount()).isEqualTo(2);
        assertThat(paymentMapper.findById(outbox.getPaymentId()).orElseThrow().getPaidAt()).isEqualTo(paidAt);
        verify(producer, times(2)).publish(any(PaymentOutbox.class));
    }

    @Test
    void concurrentExpirationAndDeliveryKeepReservationAndPaymentConsistent() throws Exception {
        PaymentRequestedMessage message = objectMapper.readValue(outbox.getPayload(), PaymentRequestedMessage.class);
        Reservation reservation = reservationMapper.findById(message.reservationId());
        ReflectionTestUtils.setField(reservation, "expiredAt", LocalDateTime.now().minusMinutes(1));
        reservationMapper.update(reservation);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> expiration = executor.submit(() -> {
                start.await();
                expirationService.expirePendingReservation(message.reservationId());
                return null;
            });
            Future<?> delivery = executor.submit(() -> {
                start.await();
                listener.handlePaymentRequested(message);
                return null;
            });
            start.countDown();
            expiration.get(15, TimeUnit.SECONDS);
            delivery.get(15, TimeUnit.SECONDS);
            assertThat(paymentMapper.findById(outbox.getPaymentId()).orElseThrow().getStatus())
                    .isEqualTo(Payment.PaymentStatus.FAILED);
            assertThat(reservationMapper.findById(reservation.getReservationId()).getStatus())
                    .isEqualTo(Reservation.ReservationStatus.CANCELLED);
            assertThat(seatMapper.findById(reservation.getSeatId()).orElseThrow().getStatus())
                    .isEqualTo(Seat.SeatStatus.AVAILABLE);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private PaymentOutbox reload() {
        return outboxMapper.findById(outbox.getId()).orElseThrow();
    }

    private void expireClaim() {
        jdbcTemplate.update("UPDATE payment_outbox SET locked_until = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE id = ?",
                outbox.getId());
    }

    private void makeReadyNow() {
        jdbcTemplate.update("UPDATE payment_outbox SET available_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE id = ?",
                outbox.getId());
    }
}
