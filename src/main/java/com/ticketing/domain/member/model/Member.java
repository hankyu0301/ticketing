package com.ticketing.domain.member.model;

import lombok.*;
import com.ticketing.global.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class Member extends BaseEntity {

    private Long memberId;
    private String email;
    private String password;
    private String nickname;
    private Long point;

    @Builder
    private Member(String email, String password, String nickname, Long point) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.point = point != null ? point : 0L;
    }

    public static Member create(String email, String password, String nickname) {
        return Member.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .point(0L)
                .build();
    }
}
