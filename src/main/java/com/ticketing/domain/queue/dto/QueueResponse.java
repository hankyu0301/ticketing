package com.ticketing.domain.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueResponse {
    private Long rank;
    private Long retryAfter; // 다음 요청까지 기다려야 할 시간(초)
    private boolean entered; // 입장 여부
}
