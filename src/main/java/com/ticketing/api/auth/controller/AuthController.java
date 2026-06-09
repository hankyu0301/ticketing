package com.ticketing.api.auth.controller;

import com.ticketing.global.common.response.ResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/csrf")
    public ResponseEntity<ResponseDTO<Map<String, String>>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken()
        )));
    }
}
