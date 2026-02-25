package com.ticketing.api.member.dto;

import com.ticketing.domain.member.model.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.ticketing.domain.member.exception.MemberErrorMessage.*;

@Getter
@NoArgsConstructor
public class MemberSignupRequest {

    @NotBlank(message = EMAIL_MUST_NOT_EMPTY)
    @Email(message = CHECK_REQUEST_EMAIL)
    private String email;

    @NotBlank(message = PSWD_MUST_NOT_EMPTY)
    private String password;

    @NotBlank(message =  NICKNAME_MUST_NOT_EMPTY)
    private String nickname;

    public Member toEntity(String encodedPassword) {
        return Member.create(this.email, encodedPassword, this.nickname);
    }
}
