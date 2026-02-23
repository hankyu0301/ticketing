package com.ticketing.domain.member;


import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.api.member.dto.MemberSignupResponse;
import com.ticketing.infrastructure.persistence.member.MemberMapper;
import com.ticketing.application.member.service.MemberService;
import com.ticketing.domain.member.exception.MemberBadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static com.ticketing.domain.member.exception.MemberErrorMessage.DUPLICATED_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService service;
    @Mock
    MemberMapper memberMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("[SUCCESS] 회원가입 성공")
    void singup_success() {
        // given
        MemberSignupRequest validRequestDto = new MemberSignupRequest();
        ReflectionTestUtils.setField(validRequestDto, "email", "test@example.com");
        ReflectionTestUtils.setField(validRequestDto, "password", "Password123!");
        ReflectionTestUtils.setField(validRequestDto, "nickname", "테스터");

        MemberSignupResponse responseDTO = service.signUp(validRequestDto);
        assertThat(responseDTO.getEmail()).isNotNull();
    }

    @Test
    @DisplayName("[ERROR] 회원가입 실패 : 중복 이메일 입력")
    void singup_fail_duplicated_email() {
        // given
        MemberSignupRequest duplicatedEmailRequestDto = new MemberSignupRequest();
        ReflectionTestUtils.setField(duplicatedEmailRequestDto, "email", "test@example.com");
        ReflectionTestUtils.setField(duplicatedEmailRequestDto, "password", "Password123!");
        ReflectionTestUtils.setField(duplicatedEmailRequestDto, "nickname", "테스터");

        when(memberMapper.existByEmail(any())).thenReturn(true);
        // then
        assertThatThrownBy(() -> service.signUp(duplicatedEmailRequestDto))
                .isInstanceOf(MemberBadRequestException.class)
                .hasMessageStartingWith(DUPLICATED_EMAIL);
    }
}
