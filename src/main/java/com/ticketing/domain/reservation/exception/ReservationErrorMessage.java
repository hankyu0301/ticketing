package com.ticketing.domain.reservation.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReservationErrorMessage {

    public static final String SCHEDULE_ID_NOT_EMPTY = "공연 회차 ID를 입력해주세요.";
    public static final String SEAT_ID_MUST_NOT_EMPTY = "좌석 ID를 입력해주세요.";
    public static final String SEAT_NOT_BELONG_TO_SCHEDULE = "해당 회차에 속하지 않는 좌석입니다.";
    public static final String RESERVATION_TOO_BUSY = "예약 요청이 몰려 잠시 후 다시 시도해주세요.";

}
