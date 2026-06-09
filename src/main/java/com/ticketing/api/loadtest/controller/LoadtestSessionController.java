package com.ticketing.api.loadtest.controller;

import com.ticketing.api.loadtest.dto.LoadtestSessionRequest;
import com.ticketing.api.loadtest.dto.LoadtestSessionResponse;
import com.ticketing.global.common.response.ResponseDTO;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("loadtest")
@RestController
@RequestMapping("/api/v1/loadtest")
@RequiredArgsConstructor
public class LoadtestSessionController {

    private final CustomUserDetailsService customUserDetailsService;

    @PostMapping("/sessions")
    public ResponseEntity<ResponseDTO<LoadtestSessionResponse>> createSession(
            @RequestBody @Valid LoadtestSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(request.getEmail());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        return ResponseEntity.ok(ResponseDTO.getSuccessResult(LoadtestSessionResponse.from(userDetails)));
    }
}
