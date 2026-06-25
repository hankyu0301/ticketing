package com.ticketing.application.seat.event;

public record SeatUnavailableCacheEvictEvent(Long scheduleId, Long seatId) {
}
