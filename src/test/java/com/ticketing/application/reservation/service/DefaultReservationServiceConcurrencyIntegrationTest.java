package com.ticketing.application.reservation.service;

import com.ticketing.domain.seat.exception.SeatAlreadyReservedException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultReservationServiceConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private DefaultReservationService reservationService;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketingTestDataSetup testDataSetup;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Long scheduleId;
    private Long seatId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {
        databaseCleaner.execute();
        TicketingTestDataSetup.TestData data = testDataSetup.setupConcertAndSeat();
        scheduleId = data.scheduleId();
        seatId = data.seatId();
        memberIds = testDataSetup.setupMembers(THREAD_COUNT);
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.execute();
    }

    @Test
    @DisplayName("분산락 없이 동시에 예약해도 DB 조건부 갱신으로 한 요청만 성공한다")
    void reserveAllowsOnlyOneRequestWithoutDistributedLock() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (Long memberId : memberIds) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        reservationService.reserve(seatId, memberId, scheduleId);
                        successCount.incrementAndGet();
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }

            boolean allThreadsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            boolean allThreadsDone = done.await(15, TimeUnit.SECONDS);

            assertThat(allThreadsReady).isTrue();
            assertThat(allThreadsDone).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures)
                .hasSize(THREAD_COUNT - 1)
                .allMatch(SeatAlreadyReservedException.class::isInstance);

        Seat reservedSeat = seatMapper.findById(seatId).orElseThrow();
        assertThat(reservedSeat.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARY);

        Long reservationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE seat_id = ?",
                Long.class,
                seatId
        );
        assertThat(reservationCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("공연 시작 이후에는 DB 조건부 갱신도 좌석 예약을 거부한다")
    void reserveConditionalUpdateRejectsAfterConcertStarts() {
        jdbcTemplate.update(
                "UPDATE concert_schedules SET concert_date = DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE schedule_id = ?",
                scheduleId
        );

        int updatedRows = seatMapper.reserveIfAvailable(seatId, scheduleId, memberIds.get(0));

        assertThat(updatedRows).isZero();
        Seat seat = seatMapper.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
        assertThat(seat.getMemberId()).isNull();
    }
}
