package com.ticketing.global.common.exception.concert;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConcertErrorMessage {

    public static final String TITLE_MUST_NOT_EMPTY = "제목을 입력해주세요.";
    public static final String DESCRIPTION_MUST_NOT_EMPTY = "설명 입력해주세요.";
    public static final String CONCERT_DATE_MUST_NOT_EMPTY = "닉네임을 입력해주세요.";
    public static final String CONCERT_DATE_MUST_BE_FUTURE = "공연 일시는 현재 이후여야 합니다.";
    public static final String RESERVATION_OPEN_AT_MUST_NOT_EMPTY = "예매 오픈 일시를 입력해주세요.";
    public static final String TOTAL_SCORE_MUST_NOT_EMPTY = "총 좌석 수를 입력해주세요.";
    public static final String TOTAL_SCORE_MUST_BE_POSITIVE = "총 좌석 수는 0보다 커야 합니다.";
    public static final String NOT_EXIST_CONCERT = "존재하지 않는 콘서트 입니다.";

}
