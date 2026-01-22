package com.ticketing.global.common.exception.seat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeatErrorMessage {

    public static final String NOT_EXIST_SEAT = "존재하지 않는 좌석입니다.";
    public static final String ALREADY_RESERVED = "이미 예약된 좌석입니다.";
    public static final String TOTAL_SEATS_MUST_NOT_EMPTY = "총 좌석 수를 입력해주세요.";
    public static final String TOTAL_SEATS_MUST_BE_POSITIVE = "총 좌석 수는 0보다 커야 합니다.";
    public static final String SEAT_PRICE_MUST_NOT_EMPTY = "좌석 가격을 입력해주세요.";
    public static final String SEAT_PRICE_MUST_BE_POSITIVE_OR_ZERO = "좌석 가격은 0 이상 이어야 합니다.";
    public static final String SEAT_GRADE_MUST_NOT_EMPTY = "좌석 등급을 입력해주세요.";

}
