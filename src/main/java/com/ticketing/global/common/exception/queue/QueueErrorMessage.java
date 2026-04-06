package com.ticketing.global.common.exception.queue;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueErrorMessage {

    public static final String NOT_EXIST_QUEUE = "존재하지 않는 대기열입니다.";
    public static final String RESERVATION_NOT_OPEN = "아직 예매 오픈 전입니다.";
    public static final String INVALID_ESTIMATED_THROUGHPUT = "queue.estimated-throughput 는 0보다 커야 합니다.";
    public static final String INVALID_MAX_ACTIVE_MEMBER = "queue.max-active-member 는 0보다 커야 합니다.";
    public static final String INVALID_ACTIVE_TIME = "queue.active-time-ms 는 0보다 커야 합니다.";
}
