package com.ticketing.application.reservation.scheduler;

import com.ticketing.application.reservation.service.ReservationExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpirationScheduler {

    private final ReservationExpirationService reservationExpirationService;

    @Value("${ticketing.reservation.expiration.process-limit:300}")
    private int processLimit;

    @Scheduled(fixedDelayString = "${ticketing.reservation.expiration.scan-delay-ms:60000}")
    @SchedulerLock(
            name = "ReservationExpirationScheduler_expirePendingReservations",
            lockAtLeastFor = "${ticketing.reservation.expiration.lock-at-least-for:PT1S}",
            lockAtMostFor = "${ticketing.reservation.expiration.lock-at-most-for:PT2M}"
    )
    public void expirePendingReservations() {
        LocalDateTime scanTime = LocalDateTime.now();
        List<Long> reservationIds = reservationExpirationService.findExpiredPendingReservationIds(scanTime, processLimit);
        if (reservationIds.isEmpty()) {
            return;
        }

        for (Long reservationId : reservationIds) {
            try {
                reservationExpirationService.expirePendingReservation(reservationId);
            } catch (Exception e) {
                log.error("Failed to expire reservation {}", reservationId, e);
            }
        }
    }
}
