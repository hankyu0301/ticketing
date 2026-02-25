package com.ticketing.api.seat.controller;

import com.ticketing.api.seat.dto.SeatCreateRequest;
import com.ticketing.api.seat.dto.SeatResponse;
import com.ticketing.application.seat.service.SeatService;
import com.ticketing.global.aop.ActiveTokenRequired;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules/{scheduleId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping
    public ResponseEntity<ResponseDTO<Void>> createSeats(
            @PathVariable Long scheduleId,
            @Valid @RequestBody SeatCreateRequest request) {
        seatService.createSeats(scheduleId, request);
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(null));
    }

    @ActiveTokenRequired
    @GetMapping
    public ResponseEntity<ResponseDTO<List<SeatResponse>>> getSeats(
            @PathVariable Long scheduleId) {
        List<SeatResponse> seats = seatService.getSeats(scheduleId);
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(seats));
    }
}