package com.ticketing.domain.member.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberErrorMessage {

    public static final String CHECK_REQUEST_EMAIL = "이메일 포맷은 'xxx@xxx.xx' 혹은 'xxx@xxx.xxx' 입니다.";
    public static final String EMAIL_MUST_NOT_EMPTY = "이메일을 입력해주세요.";
    public static final String PSWD_MUST_NOT_EMPTY = "비밀번호를 입력해주세요.";
    public static final String NICKNAME_MUST_NOT_EMPTY = "닉네임을 입력해주세요.";
    public static final String DUPLICATED_EMAIL = "이미 해당 이메일이 존재합니다.";
    public static final String CHECK_EMAIL_OR_PSWD = "아이디 또는 비밀번호가 일치하지 않습니다.";
    public static final String AUTHORIZED_BUT_NOT_AUTHENTICATED = "인증되었지만 접근 권한이 없습니다.";
    public static final String NOT_EXIST_MEMBER = "존재하지 않는 유저 입니다.";
}
