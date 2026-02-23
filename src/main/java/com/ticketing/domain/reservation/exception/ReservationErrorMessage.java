package com.ticketing.domain.reservation.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReservationErrorMessage {

    public static final String SCHEDULE_ID_NOT_EMPTY = "공연 스케줄 ID를 입력해주세요.";
    public static final String SEAT_ID_MUST_NOT_EMPTY = "좌석 ID를 입력해주세요.";

}
