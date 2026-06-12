package com.ticketing.infrastructure.persistence.seat;

import com.ticketing.application.seat.event.SeatUnavailableCacheEvictEvent;
import com.ticketing.application.seat.event.SeatUnavailableCacheMarkSoldOutEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SeatUnavailableCacheEventListener {

    private final SeatUnavailableCacheRepository seatUnavailableCacheRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMarkSoldOut(SeatUnavailableCacheMarkSoldOutEvent event) {
        seatUnavailableCacheRepository.markSoldOut(event.scheduleId(), event.seatId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEvict(SeatUnavailableCacheEvictEvent event) {
        seatUnavailableCacheRepository.evict(event.scheduleId(), event.seatId());
    }
}
