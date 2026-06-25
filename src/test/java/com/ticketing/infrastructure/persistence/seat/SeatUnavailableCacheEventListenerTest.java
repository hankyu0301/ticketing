package com.ticketing.infrastructure.persistence.seat;

import com.ticketing.application.seat.event.SeatUnavailableCacheEvictEvent;
import com.ticketing.application.seat.event.SeatUnavailableCacheMarkSoldOutEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatUnavailableCacheEventListenerTest {

    @Mock
    private SeatUnavailableCacheRepository seatUnavailableCacheRepository;

    @InjectMocks
    private SeatUnavailableCacheEventListener listener;

    @Test
    @DisplayName("좌석 판매 완료 이벤트를 받으면 unavailable cache를 SOLD_OUT으로 갱신한다")
    void handleMarkSoldOut_marksSoldOut() {
        listener.handleMarkSoldOut(new SeatUnavailableCacheMarkSoldOutEvent(1L, 10L));

        verify(seatUnavailableCacheRepository).markSoldOut(1L, 10L);
    }

    @Test
    @DisplayName("예약 만료 이벤트를 받으면 unavailable cache를 제거한다")
    void handleEvict_evictsCache() {
        listener.handleEvict(new SeatUnavailableCacheEvictEvent(1L, 10L));

        verify(seatUnavailableCacheRepository).evict(1L, 10L);
    }
}
