package com.ticketing.domain.member.controller;

import com.ticketing.domain.member.dto.MemberSignupRequest;
import com.ticketing.domain.member.service.MemberService;
import com.ticketing.global.common.response.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<ResponseDTO> signup(@Valid @RequestBody MemberSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.getSuccessResult(memberService.signUp(request)));
    }
}
