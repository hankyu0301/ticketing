package com.ticketing.domain.member.dto;

import com.ticketing.domain.member.model.Member;
import lombok.Generated;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberSignupResponse {

    private String email;
    private String nickname;

    public MemberSignupResponse(Member member) {
        this.email = member.getEmail();
        this.nickname = member.getNickname();
    }
}
