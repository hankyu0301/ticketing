package com.ticketing.api.reservation.controller;

import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.service.ReservationService;
import com.ticketing.global.aop.ActiveTokenRequired;
import com.ticketing.global.aop.ScheduleIdParam;
import com.ticketing.global.common.response.ResponseDTO;
import com.ticketing.global.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @ActiveTokenRequired
    @PostMapping
    public ResponseEntity<ResponseDTO<ReservationResponse>> createReservation(@AuthenticationPrincipal CustomUserDetails member, @ScheduleIdParam @RequestBody @Valid ReservationCreateRequest request) {
        Long memberId = member.getSessionMember().getMemberId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.getSuccessResult(reservationService.reserve(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ResponseDTO<List<ReservationResponse>>> getReservations(@AuthenticationPrincipal CustomUserDetails member) {
        Long memberId = member.getSessionMember().getMemberId();
        return ResponseEntity.status(HttpStatus.OK).body(ResponseDTO.getSuccessResult(reservationService.getReservations(memberId)));
    }
}
