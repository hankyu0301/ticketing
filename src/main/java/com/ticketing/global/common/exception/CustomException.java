package com.ticketing.global.common.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class CustomException extends RuntimeException {
    protected CustomException(String message) {
        super(message);
    }
}
