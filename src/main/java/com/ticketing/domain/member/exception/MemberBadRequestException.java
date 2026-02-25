package com.ticketing.domain.member.exception;

import com.ticketing.global.common.exception.CustomException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberBadRequestException  extends CustomException {
    public MemberBadRequestException(String message) {
        super(message);
    }
}
