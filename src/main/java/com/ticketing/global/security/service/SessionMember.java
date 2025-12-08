package com.ticketing.global.security.service;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

@Getter
public class SessionMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long memberId;
    private String email;
    private String nickname;

    private SessionMember() {}

    public static SessionMember create(Long memberId, String email, String nickname) {
        SessionMember sessionMember = new SessionMember();
        sessionMember.memberId = memberId;
        sessionMember.email = email;
        sessionMember.nickname = nickname;
        return sessionMember;
    }
}