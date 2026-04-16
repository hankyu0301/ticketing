package com.ticketing.api.queue.controller;

import com.ticketing.api.queue.dto.QueueResponse;
import com.ticketing.application.queue.service.QueueService;
import com.ticketing.global.common.response.ResponseDTO;
import com.ticketing.global.security.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    // 1. 대기열 진입 (대기 인원 없으면 바로 입장)
    @PostMapping("/enter")
    public ResponseEntity<ResponseDTO<QueueResponse>> enterQueue(
            @RequestParam Long scheduleId,
            @AuthenticationPrincipal CustomUserDetails member) {

        QueueResponse response = queueService.enterQueue(scheduleId, member.getSessionMember().getMemberId());
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(response));
    }

    // 2. 순번 조회 (Polling)
    @GetMapping("/rank")
    public ResponseEntity<ResponseDTO<QueueResponse>> getRank(
            @RequestParam Long scheduleId,
            @AuthenticationPrincipal CustomUserDetails member) {

        QueueResponse response = queueService.getQueueStatus(scheduleId, member.getSessionMember().getMemberId());

        return ResponseEntity.ok(ResponseDTO.getSuccessResult(response));
    }

    @DeleteMapping("/active/{scheduleId}")
    public ResponseEntity<ResponseDTO<Void>> leaveActive(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal CustomUserDetails member) {

        queueService.leaveActive(scheduleId, member.getSessionMember().getMemberId());
        return ResponseEntity.ok(ResponseDTO.getSuccessResult(null));
    }
}
