package com.ticketing.global.security.handler.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.ticketing.domain.member.exception.MemberErrorMessage.CHECK_EMAIL_OR_PSWD;

@Slf4j
@RequiredArgsConstructor
@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        logErrorDetails(exception, request.getRequestURI());
        handleFailureResponse(response);
    }

    private void logErrorDetails(AuthenticationException authenticationException, String requestUri) {
        log.error("Authentication failed", authenticationException);
        log.error("Request Uri: {}", requestUri);
    }

    private void handleFailureResponse(HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(ResponseDTO.getFailResult(CHECK_EMAIL_OR_PSWD)));
    }
}
