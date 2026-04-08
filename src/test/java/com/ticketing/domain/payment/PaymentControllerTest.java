package com.ticketing.domain.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.payment.controller.PaymentController;
import com.ticketing.api.payment.dto.PaymentCreateRequest;
import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.application.payment.service.PaymentService;
import com.ticketing.domain.payment.exception.PaymentBadRequestException;
import com.ticketing.domain.payment.exception.PaymentErrorMessage;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.SessionMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    @DisplayName("동기 결제 요청이 성공하면 201 Created를 반환한다.")
    void syncPaymentSuccess() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest();
        ReflectionTestUtils.setField(request, "reservationId", 1L);

        given(paymentService.requestSync(anyLong(), anyLong())).willReturn(createPaymentResponse("COMPLETED"));

        ResultActions resultActions = mockMvc.perform(post("/api/v1/payments/sync")
                .with(csrf())
                .with(authentication(createAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        resultActions
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @DisplayName("비동기 결제 요청이 성공하면 202 Accepted를 반환한다.")
    void asyncPaymentSuccess() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest();
        ReflectionTestUtils.setField(request, "reservationId", 1L);

        given(paymentService.requestAsync(anyLong(), anyLong())).willReturn(createPaymentResponse("PENDING"));

        ResultActions resultActions = mockMvc.perform(post("/api/v1/payments/async")
                .with(csrf())
                .with(authentication(createAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        resultActions
                .andExpect(status().isAccepted())
                .andDo(print());
    }

    @Test
    @DisplayName("결제 조회가 성공하면 200 OK를 반환한다.")
    void getPaymentSuccess() throws Exception {
        given(paymentService.getPayment(1L, 100L)).willReturn(createPaymentResponse("COMPLETED"));

        mockMvc.perform(get("/api/v1/payments/100")
                        .with(authentication(createAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("reservationId가 없으면 400 Bad Request를 반환한다.")
    void paymentValidationFail() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest();

        mockMvc.perform(post("/api/v1/payments/sync")
                        .with(csrf())
                        .with(authentication(createAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(PaymentErrorMessage.RESERVATION_ID_MUST_NOT_EMPTY)))
                .andDo(print());
    }

    @Test
    @DisplayName("결제 불가 상태면 400 Bad Request를 반환한다.")
    void paymentBadRequest() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest();
        ReflectionTestUtils.setField(request, "reservationId", 1L);

        given(paymentService.requestSync(anyLong(), anyLong()))
                .willThrow(new PaymentBadRequestException(PaymentErrorMessage.PAYMENT_NOT_ALLOWED));

        mockMvc.perform(post("/api/v1/payments/sync")
                        .with(csrf())
                        .with(authentication(createAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(PaymentErrorMessage.PAYMENT_NOT_ALLOWED)))
                .andDo(print());
    }

    private Authentication createAuthentication(Long memberId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                SessionMember.create(memberId, "user@test.com", "tester"),
                "password",
                Collections.emptyList()
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private PaymentResponse createPaymentResponse(String status) {
        PaymentResponse response = new PaymentResponse();
        ReflectionTestUtils.setField(response, "paymentId", 100L);
        ReflectionTestUtils.setField(response, "reservationId", 1L);
        ReflectionTestUtils.setField(response, "memberId", 1L);
        ReflectionTestUtils.setField(response, "amount", BigDecimal.valueOf(10000));
        ReflectionTestUtils.setField(response, "status", status);
        ReflectionTestUtils.setField(response, "paidAt", LocalDateTime.now());
        return response;
    }
}
