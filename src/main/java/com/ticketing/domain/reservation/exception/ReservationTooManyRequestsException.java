package com.ticketing.domain.reservation.exception;

import com.ticketing.global.common.exception.CustomException;

public class ReservationTooManyRequestsException extends CustomException {
    public ReservationTooManyRequestsException(String message) {
        super(message);
    }
}
