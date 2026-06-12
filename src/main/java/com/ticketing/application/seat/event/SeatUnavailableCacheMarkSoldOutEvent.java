package com.ticketing.application.seat.event;

public record SeatUnavailableCacheMarkSoldOutEvent(Long scheduleId, Long seatId) {
}
