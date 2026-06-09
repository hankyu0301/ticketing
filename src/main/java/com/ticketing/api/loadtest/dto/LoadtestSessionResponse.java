package com.ticketing.api.loadtest.dto;

import com.ticketing.global.security.service.CustomUserDetails;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoadtestSessionResponse {

    private Long memberId;
    private String email;
    private String nickname;

    private LoadtestSessionResponse(Long memberId, String email, String nickname) {
        this.memberId = memberId;
        this.email = email;
        this.nickname = nickname;
    }

    public static LoadtestSessionResponse from(CustomUserDetails userDetails) {
        return new LoadtestSessionResponse(
                userDetails.getSessionMember().getMemberId(),
                userDetails.getSessionMember().getEmail(),
                userDetails.getSessionMember().getNickname()
        );
    }
}
