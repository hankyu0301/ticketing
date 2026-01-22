package com.ticketing.global.common.exception.concert;

import com.ticketing.global.common.exception.CustomException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConcertBadRequestException extends CustomException {
    public ConcertBadRequestException(String message) {
        super(message);
    }
}
