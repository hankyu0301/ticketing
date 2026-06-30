package com.ticketing.infrastructure.persistence.reservation;

import com.ticketing.domain.reservation.model.Reservation;
import com.ticketing.support.DatabaseCleaner;
import com.ticketing.support.IntegrationTestSupport;
import com.ticketing.support.TicketingTestDataSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationMapperIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketingTestDataSetup testDataSetup;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.execute();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.execute();
    }

    @Test
    @DisplayName("회원 예약 이력은 예약 시각과 예약 ID를 기준으로 최신순 조회된다")
    void findAllByMemberIdReturnsNewestFirst() {
        TicketingTestDataSetup.TestData data = testDataSetup.setupConcertAndSeat();
        Long memberId = testDataSetup.setupMembers(1).get(0);
        LocalDateTime oldestReservedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime newestReservedAt = LocalDateTime.of(2026, 1, 2, 10, 0);

        Long oldestReservationId = insertReservation(memberId, data, oldestReservedAt);
        Long firstNewestReservationId = insertReservation(memberId, data, newestReservedAt);
        Long secondNewestReservationId = insertReservation(memberId, data, newestReservedAt);

        List<Reservation> reservations = reservationMapper.findAllByMemberId(memberId);

        assertThat(reservations)
                .extracting(Reservation::getReservationId)
                .containsExactly(secondNewestReservationId, firstNewestReservationId, oldestReservationId);
    }

    private Long insertReservation(Long memberId,
                                   TicketingTestDataSetup.TestData data,
                                   LocalDateTime reservedAt) {
        jdbcTemplate.update("""
                INSERT INTO reservations
                    (member_id, schedule_id, seat_id, status, reserved_at, expired_at, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', ?, NULL, NOW(), NOW())
                """,
                memberId,
                data.scheduleId(),
                data.seatId(),
                reservedAt);

        return jdbcTemplate.queryForObject(
                "SELECT MAX(reservation_id) FROM reservations WHERE member_id = ?",
                Long.class,
                memberId
        );
    }
}
