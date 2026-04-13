package com.ticketing.api.payment.controller;

import com.ticketing.api.payment.dto.PaymentCreateRequest;
import com.ticketing.api.payment.dto.PaymentResponse;
import com.ticketing.application.payment.service.PaymentService;
import com.ticketing.global.common.response.ResponseDTO;
import com.ticketing.global.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/sync")
    public ResponseEntity<ResponseDTO<PaymentResponse>> requestSyncPayment(
            @AuthenticationPrincipal CustomUserDetails member,
            @RequestBody @Valid PaymentCreateRequest request
    ) {
        Long memberId = member.getSessionMember().getMemberId();
        PaymentResponse response = paymentService.requestSync(memberId, request.getReservationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.getSuccessResult(response));
    }

    @PostMapping("/async")
    public ResponseEntity<ResponseDTO<PaymentResponse>> requestAsyncPayment(
            @AuthenticationPrincipal CustomUserDetails member,
            @RequestBody @Valid PaymentCreateRequest request
    ) {
        Long memberId = member.getSessionMember().getMemberId();
        PaymentResponse response = paymentService.requestAsync(memberId, request.getReservationId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseDTO.getSuccessResult(response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseDTO<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails member,
            @PathVariable Long paymentId
    ) {
        Long memberId = member.getSessionMember().getMemberId();
        PaymentResponse response = paymentService.getPayment(memberId, paymentId);
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(response));
    }
}
