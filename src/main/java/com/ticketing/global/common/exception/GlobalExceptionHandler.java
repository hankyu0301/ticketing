package com.ticketing.global.common.exception;

import com.ticketing.global.common.response.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;

@Slf4j
@RestControllerAdvice
public final class GlobalExceptionHandler {

    public static final String HTTP_REQUEST = "> Http Method : {},  URI : {}, msg : {}, status : {}";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<String>> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        log.info(HTTP_REQUEST, request.getMethod(), request.getRequestURI(), ex.getMessage(), HttpStatus.BAD_REQUEST);

        LinkedHashMap<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
        });

        return ResponseEntity.badRequest().body(ResponseDTO.getFailResult(fieldErrors.toString()));
    }
}
