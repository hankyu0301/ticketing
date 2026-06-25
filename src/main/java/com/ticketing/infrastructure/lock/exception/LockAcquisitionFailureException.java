package com.ticketing.infrastructure.lock.exception;

public class LockAcquisitionFailureException extends RuntimeException {
    public LockAcquisitionFailureException(String message) {
        super(message);
    }
}
