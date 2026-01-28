package com.ticketing.domain.reservation.controller;

import com.ticketing.domain.reservation.dto.ReservationCreateRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.service.ReservationService;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ResponseDTO<ReservationResponse>> createReservation(@RequestBody @Valid ReservationCreateRequest request) {
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(reservationService.reserve(request)));
    }
}