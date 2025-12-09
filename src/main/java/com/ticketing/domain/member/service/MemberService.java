package com.ticketing.domain.member.service;

import com.ticketing.domain.member.dto.MemberSignupRequest;
import com.ticketing.domain.member.dto.MemberSignupResponse;
import com.ticketing.domain.member.mapper.MemberMapper;
import com.ticketing.domain.member.model.Member;
import com.ticketing.global.common.exception.member.MemberBadRequestException;
import com.ticketing.global.common.response.ResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ticketing.global.common.exception.member.MemberErrorMessage.DUPLICATED_EMAIL;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberSignupResponse signUp(MemberSignupRequest req) {

        validateEmailNotDuplicated(req.getEmail());
        String encodedPassword = passwordEncoder.encode(req.getPassword());
        Member member = req.toEntity(encodedPassword);
        memberMapper.insertMember(member);
        return new MemberSignupResponse(member);

    }

    private void validateEmailNotDuplicated(String email) {
        boolean isDuplicated = memberMapper.existByEmail(email);
        if (isDuplicated) {
            throw new MemberBadRequestException(DUPLICATED_EMAIL);
        }
    }
}
