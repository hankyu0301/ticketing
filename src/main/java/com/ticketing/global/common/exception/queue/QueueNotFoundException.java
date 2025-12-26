package com.ticketing.global.common.exception.queue;

import com.ticketing.global.common.exception.CustomException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueNotFoundException extends CustomException {
    public QueueNotFoundException(String message) {
        super(message);
    }
}
