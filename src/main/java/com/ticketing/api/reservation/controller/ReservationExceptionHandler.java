package com.ticketing.api.reservation.controller;

import com.ticketing.domain.reservation.exception.ReservationBadRequestException;
import com.ticketing.domain.reservation.exception.ReservationTooManyRequestsException;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.ticketing.global.common.exception.GlobalExceptionHandler.HTTP_REQUEST;

@Slf4j
@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler(ReservationBadRequestException.class)
    public ResponseEntity<ResponseDTO<String>> badRequestException(ReservationBadRequestException ex, HttpServletRequest request) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(),
                ex.getMessage(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseDTO.getFailResult(ex.getMessage()));
    }

    @ExceptionHandler(ReservationTooManyRequestsException.class)
    public ResponseEntity<ResponseDTO<String>> tooManyRequestsException(ReservationTooManyRequestsException ex, HttpServletRequest request) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(),
                ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(ResponseDTO.getFailResult(ex.getMessage()));
    }

}
