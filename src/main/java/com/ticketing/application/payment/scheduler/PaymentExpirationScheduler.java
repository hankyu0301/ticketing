package com.ticketing.application.payment.scheduler;

import com.ticketing.application.payment.service.PaymentExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpirationScheduler {

    private final PaymentExpirationService paymentExpirationService;

    @Value("${ticketing.payment.expiration.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${ticketing.payment.expiration.scan-delay-ms:60000}")
    @SchedulerLock(
            name = "PaymentExpirationScheduler_expireReservations",
            lockAtLeastFor = "${scheduler.lock-at-least-for:PT1S}",
            lockAtMostFor = "${scheduler.lock-at-most-for:PT10S}"
    )
    public void expireReservations() {
        List<Long> previousBatch = Collections.emptyList();

        while (true) {
            List<Long> reservationIds = paymentExpirationService.findExpiredPendingReservationIds(LocalDateTime.now(), batchSize);
            if (reservationIds.isEmpty()) {
                return;
            }

            if (reservationIds.equals(previousBatch)) {
                log.warn("Stopping expiration scan because the same batch repeated: {}", reservationIds);
                return;
            }

            previousBatch = reservationIds;
            for (Long reservationId : reservationIds) {
                try {
                    paymentExpirationService.expireReservation(reservationId);
                } catch (Exception e) {
                    log.error("Failed to expire reservation {}", reservationId, e);
                }
            }

            if (reservationIds.size() < batchSize) {
                return;
            }
        }
    }
}
