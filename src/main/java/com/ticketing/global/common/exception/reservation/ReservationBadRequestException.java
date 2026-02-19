package com.ticketing.global.common.exception.reservation;

import com.ticketing.global.common.exception.CustomException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReservationBadRequestException extends CustomException {
    public ReservationBadRequestException(String message) {
        super(message);
    }
}
