package com.ticketing.application.reservation.service;

import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.application.payment.service.PaymentService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.session.store-type=none")
class ReservationExpirationServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReservationExpirationService reservationExpirationService;

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
    @DisplayName("만료된 예약을 정리하면 예약은 취소되고 대기 중 결제는 실패 처리된다")
    void expirePendingReservationCancelsExpiredReservationAndFailsPendingPayment() {
        Long reservationId = createReservation(memberId);
        PaymentResponse pendingResponse = paymentService.requestAsync(memberId, reservationId);

        Reservation reservation = reservationMapper.findById(reservationId);
        ReflectionTestUtils.setField(reservation, "expiredAt", LocalDateTime.now().minusMinutes(1));
        reservationMapper.update(reservation);

        reservationExpirationService.expirePendingReservation(reservationId);
        paymentMessageListener.handlePaymentRequested(
                new PaymentRequestedMessage(pendingResponse.getPaymentId(), reservationId, memberId)
        );

        Payment payment = paymentMapper.findById(pendingResponse.getPaymentId()).orElseThrow();
        Reservation expiredReservation = reservationMapper.findById(reservationId);
        Seat releasedSeat = seatMapper.findById(seatId).orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(expiredReservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
        assertThat(releasedSeat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
        assertThat(releasedSeat.getMemberId()).isNull();
    }

    private Long createReservation(Long memberId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return reservationFacade.reserve(memberId, request).getReservationId();
    }
}
