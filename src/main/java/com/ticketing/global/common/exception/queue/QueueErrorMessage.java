package com.ticketing.global.common.exception.queue;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueErrorMessage {

    public static final String NOT_EXIST_QUEUE = "존재하지 않는 대기열 입니다.";

}
