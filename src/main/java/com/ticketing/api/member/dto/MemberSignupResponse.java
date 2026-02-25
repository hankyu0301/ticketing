package com.ticketing.api.member.dto;

import com.ticketing.domain.member.model.Member;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberSignupResponse {

    private String email;
    private String nickname;

    public MemberSignupResponse(Member member) {
        this.email = member.getEmail();
        this.nickname = member.getNickname();
    }
}
