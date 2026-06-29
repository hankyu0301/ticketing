package com.ticketing.application.payment.service;

import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.application.reservation.facade.ReservationFacade;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentMessageListener;
import com.ticketing.infrastructure.messaging.rabbitmq.PaymentRequestedMessage;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class PaymentServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentMessageListener paymentMessageListener;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private TicketingTestDataSetup testDataSetup;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private Long scheduleId;
    private Long seatId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        databaseCleaner.execute();
        TicketingTestDataSetup.TestData data = testDataSetup.setupConcertAndSeat();
        this.scheduleId = data.scheduleId();
        this.seatId = data.seatId();
        List<Long> memberIds = testDataSetup.setupMembers(1);
        this.memberId = memberIds.get(0);
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.execute();
    }

    @Test
    @DisplayName("동기 결제는 즉시 결제를 완료하고 예약과 좌석 상태를 확정한다.")
    void requestSyncCompletesPayment() {
        Long reservationId = createReservation(memberId);

        PaymentResponse response = paymentService.requestSync(memberId, reservationId);

        Payment payment = paymentMapper.findById(response.getPaymentId()).orElseThrow();
        Reservation reservation = reservationMapper.findById(reservationId);
        Seat seat = seatMapper.findById(seatId).orElseThrow();

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("비동기 결제는 PENDING으로 저장하고 커밋 후 메시지를 발행한 뒤 listener에서 완료된다.")
    void requestAsyncPublishesMessageAndCompletesByListener() {
        Long reservationId = createReservation(memberId);

        PaymentResponse response = paymentService.requestAsync(memberId, reservationId);

        verify(rabbitTemplate).convertAndSend(eq("ticketing.payment"), eq("payment.requested"), any(PaymentRequestedMessage.class));
        assertThat(response.getStatus()).isEqualTo("PENDING");

        paymentMessageListener.handlePaymentRequested(new PaymentRequestedMessage(response.getPaymentId(), reservationId, memberId));

        Payment payment = paymentMapper.findById(response.getPaymentId()).orElseThrow();
        Reservation reservation = reservationMapper.findById(reservationId);
        Seat seat = seatMapper.findById(seatId).orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("중복 결제 메시지가 와도 비동기 결제는 한 번만 완료된다.")
    void requestAsyncIgnoresDuplicateMessage() {
        Long reservationId = createReservation(memberId);

        PaymentResponse response = paymentService.requestAsync(memberId, reservationId);

        paymentMessageListener.handlePaymentRequested(new PaymentRequestedMessage(response.getPaymentId(), reservationId, memberId));
        Payment completed = paymentMapper.findById(response.getPaymentId()).orElseThrow();

        paymentMessageListener.handlePaymentRequested(new PaymentRequestedMessage(response.getPaymentId(), reservationId, memberId));
        Payment reloaded = paymentMapper.findById(response.getPaymentId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(reloaded.getPaidAt()).isEqualTo(completed.getPaidAt());
    }

    @Test
    @DisplayName("중복 결제 메시지가 동시에 와도 최종 결제 상태는 완료로 유지된다.")
    void requestAsyncConcurrentDuplicateMessageKeepsCompletedPayment() throws InterruptedException {
        Long reservationId = createReservation(memberId);
        PaymentResponse response = paymentService.requestAsync(memberId, reservationId);
        PaymentRequestedMessage message = new PaymentRequestedMessage(response.getPaymentId(), reservationId, memberId);

        int threadCount = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    paymentMessageListener.handlePaymentRequested(message);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdownNow();

        Payment payment = paymentMapper.findById(response.getPaymentId()).orElseThrow();
        Reservation reservation = reservationMapper.findById(reservationId);
        Seat seat = seatMapper.findById(seatId).orElseThrow();

        assertThat(completed).isTrue();
        assertThat(failCount.get()).isZero();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("FAILED 결제는 같은 payment row를 재사용해 다시 결제할 수 있다.")
    void requestSyncReusesFailedPayment() {
        Long reservationId = createReservation(memberId);
        PaymentResponse pendingResponse = paymentService.requestAsync(memberId, reservationId);

        Payment payment = paymentMapper.findById(pendingResponse.getPaymentId()).orElseThrow();
        payment.fail();
        paymentMapper.update(payment);

        PaymentResponse retried = paymentService.requestSync(memberId, reservationId);

        Payment completed = paymentMapper.findById(retried.getPaymentId()).orElseThrow();
        assertThat(retried.getPaymentId()).isEqualTo(pendingResponse.getPaymentId());
        assertThat(completed.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
    }

    private Long createReservation(Long memberId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return reservationFacade.reserve(memberId, request).getReservationId();
    }
}
