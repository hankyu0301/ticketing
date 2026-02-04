package com.ticketing.domain.reservation;

import com.ticketing.domain.reservation.application.ReservationFacade;
import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class ReservationConcurrencyTest extends IntegrationTestSupport {

    @Autowired private ReservationFacade reservationFacade;
    @Autowired private SeatMapper seatMapper;
    @Autowired private TicketingTestDataSetup testDataSetup;
    @Autowired private DatabaseCleaner databaseCleaner;

    private Long scheduleId;
    private Long seatId;
    private List<Long> memberIds;

    @AfterEach
    void tearDown() {
        databaseCleaner.execute();
    }

    @BeforeEach
    void setUp() {
        TicketingTestDataSetup.TestData data = testDataSetup.setupConcertAndSeat();

        this.scheduleId = data.scheduleId();
        this.seatId = data.seatId();

        this.memberIds = testDataSetup.setupMembers(100);
    }

    @Test
    @DisplayName("100명 동시 요청 시 1명만 성공해야 한다.")
    void pessimistic_lock_success() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Long validMemberId = memberIds.get(index);
                    ReservationCreateRequest request = createReservationRequest();
                    reservationFacade.reserve(validMemberId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("Fail cause: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(successCount.get())
                .as("동시에 100명이 요청해도 1명만 성공해야 합니다.")
                .isEqualTo(1);

        assertThat(failCount.get())
                .as("나머지 99명은 예외가 발생하여 실패해야 합니다.")
                .isEqualTo(threadCount - 1);

        // 데이터가 정합성을 유지했는지 확인
        Seat updatedSeat = seatMapper.findById(seatId).orElseThrow();

        assertThat(updatedSeat.getStatus())
                .as("좌석은 예매 완료(혹은 진행중) 상태여야 합니다.")
                .isNotEqualTo(Seat.SeatStatus.AVAILABLE);
    }

    private ReservationCreateRequest createReservationRequest() {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return request;
    }
}