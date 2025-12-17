package com.ticketing.domain.concert.controller;

import com.ticketing.domain.concert.dto.ConcertCreateRequest;
import com.ticketing.domain.concert.dto.ConcertDto;
import com.ticketing.domain.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.domain.concert.dto.ConcertScheduleDto;
import com.ticketing.domain.concert.service.ConcertService;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    /**
     * 1. 콘서트 생성
     */
    @PostMapping
    public ResponseEntity<ResponseDTO<Long>> createConcert(@Valid @RequestBody ConcertCreateRequest request) {
        Long concertId = concertService.createConcert(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.getSuccessResult(concertId));
    }

    /**
     * 2. 콘서트 일정 생성
     */
    @PostMapping("/{concertId}/schedules")
    public ResponseEntity<ResponseDTO<Long>> createSchedule(
            @PathVariable Long concertId,
            @Valid @RequestBody ConcertScheduleCreateRequest request) {

        Long scheduleId = concertService.createSchedule(concertId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.getSuccessResult(scheduleId));
    }

    /**
     * 3. 콘서트 상세 조회
     */
    @GetMapping("/{concertId}")
    public ResponseEntity<ResponseDTO<ConcertDto>> getConcert(@PathVariable Long concertId) {
        ConcertDto concert = concertService.getConcert(concertId);

        return ResponseEntity.ok(ResponseDTO.getSuccessResult(concert));
    }

    /**
     * 4. 콘서트 일정 목록 조회
     */
    @GetMapping("/{concertId}/schedules")
    public ResponseEntity<ResponseDTO<List<ConcertScheduleDto>>> getSchedules(@PathVariable Long concertId) {
        List<ConcertScheduleDto> schedules = concertService.getSchedules(concertId);

        return ResponseEntity.ok(ResponseDTO.getSuccessResult(schedules));
    }
}