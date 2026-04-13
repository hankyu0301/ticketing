package com.ticketing.domain.payment.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PaymentErrorMessage {

    public static final String RESERVATION_ID_MUST_NOT_EMPTY = "예약 ID를 입력해 주세요.";
    public static final String NOT_EXIST_PAYMENT = "존재하지 않는 결제입니다.";
    public static final String NOT_EXIST_RESERVATION = "존재하지 않는 예약입니다.";
    public static final String PAYMENT_NOT_ALLOWED = "결제할 수 없는 예약입니다.";
    public static final String PAYMENT_ALREADY_PROCESSING = "이미 처리 중인 결제입니다.";
    public static final String PAYMENT_ALREADY_COMPLETED = "이미 결제가 완료되었습니다.";
}
