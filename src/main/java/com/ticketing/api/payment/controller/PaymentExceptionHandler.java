package com.ticketing.api.payment.controller;

import com.ticketing.domain.payment.exception.PaymentBadRequestException;
import com.ticketing.domain.payment.exception.PaymentNotFoundException;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.ticketing.global.common.exception.GlobalExceptionHandler.HTTP_REQUEST;

@Slf4j
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentBadRequestException.class)
    public ResponseEntity<ResponseDTO<String>> handleBadRequest(PaymentBadRequestException ex, HttpServletRequest request) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(), ex.getMessage(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest().body(ResponseDTO.getFailResult(ex.getMessage()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ResponseDTO<String>> handleNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(), ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseDTO.getFailResult(ex.getMessage()));
    }
}
