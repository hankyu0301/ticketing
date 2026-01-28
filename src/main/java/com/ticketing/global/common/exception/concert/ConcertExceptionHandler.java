package com.ticketing.global.common.exception.concert;

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
public class ConcertExceptionHandler {

    @ExceptionHandler(ConcertNotFoundException.class)
    public ResponseEntity<ResponseDTO<String>> concertNotFountException(ConcertNotFoundException ex, HttpServletRequest request) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(),
                ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseDTO.getFailResult(ex.getMessage()));
    }

    @ExceptionHandler(ConcertBadRequestException.class)
    public ResponseEntity<ResponseDTO<String>> badRequestException(ConcertBadRequestException ex, HttpServletRequest request) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(),
                ex.getMessage(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseDTO.getFailResult(ex.getMessage()));
    }
}
