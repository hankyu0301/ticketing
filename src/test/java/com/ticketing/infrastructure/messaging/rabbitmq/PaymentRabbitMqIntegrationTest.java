package com.ticketing.infrastructure.messaging.rabbitmq;

import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.application.payment.service.PaymentService;
import com.ticketing.application.reservation.facade.ReservationFacade;
import com.ticketing.domain.payment.model.Payment;
import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.payment.PaymentMapper;
import com.ticketing.infrastructure.persistence.reservation.ReservationMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = {
        "spring.rabbitmq.dynamic=true",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "ticketing.payment.rabbitmq.listener-auto-startup=true"
})
@Testcontainers
class PaymentRabbitMqIntegrationTest extends IntegrationTestSupport {

    @Container
    static final RabbitMQContainer RABBITMQ_CONTAINER = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void configureRabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ_CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ_CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ_CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ_CONTAINER::getAdminPassword);
    }

    @Autowired
    private PaymentService paymentService;

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

    @MockitoSpyBean
    private PaymentMessageListener paymentMessageListener;

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
    @DisplayName("비동기 결제 요청은 실제 RabbitMQ를 통해 소비되고 결제를 완료한다.")
    void requestAsyncCompletesPaymentThroughRabbitMq() {
        Long reservationId = createReservation(memberId);

        PaymentResponse response = paymentService.requestAsync(memberId, reservationId);

        assertThat(response.getStatus()).isEqualTo("PENDING");

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    verify(paymentMessageListener, atLeastOnce())
                            .handlePaymentRequested(argThat(message ->
                                    message.paymentId().equals(response.getPaymentId())
                                            && message.reservationId().equals(reservationId)
                                            && message.memberId().equals(memberId)
                            ));

                    Payment payment = paymentMapper.findById(response.getPaymentId()).orElseThrow();
                    Reservation reservation = reservationMapper.findById(reservationId);
                    Seat seat = seatMapper.findById(seatId).orElseThrow();

                    assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
                    assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
                    assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.SOLD_OUT);
                });
    }

    private Long createReservation(Long memberId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return reservationFacade.reserve(memberId, request).getReservationId();
    }
}
