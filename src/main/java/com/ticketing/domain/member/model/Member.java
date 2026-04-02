package com.ticketing.domain.member.model;

import lombok.*;
import com.ticketing.global.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class Member extends BaseEntity {

    public static final String DEFAULT_ROLE = "USER";

    private Long memberId;
    private String email;
    private String password;
    private String nickname;
    private String role;
    private Long point;

    @Builder
    private Member(String email, String password, String nickname, String role, Long point) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role != null ? role : DEFAULT_ROLE;
        this.point = point != null ? point : 0L;
    }

    public static Member create(String email, String password, String nickname) {
        return Member.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .role(DEFAULT_ROLE)
                .point(0L)
                .build();
    }
}
