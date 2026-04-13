package com.ticketing.application.member.service;

import com.ticketing.api.member.dto.MemberSignupRequest;
import com.ticketing.api.member.dto.MemberSignupResponse;
import com.ticketing.application.member.service.MemberService;
import com.ticketing.domain.member.exception.MemberBadRequestException;
import com.ticketing.domain.member.model.Member;
import com.ticketing.infrastructure.persistence.member.MemberMapper;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService service;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입에 성공한다.")
    void signupSuccess() {
        MemberSignupRequest request = new MemberSignupRequest();
        ReflectionTestUtils.setField(request, "email", "test@example.com");
        ReflectionTestUtils.setField(request, "password", "Password123!");
        ReflectionTestUtils.setField(request, "nickname", "테스트");

        given(memberMapper.existByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("encoded-password");

        MemberSignupResponse response = service.signUp(request);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getNickname()).isEqualTo("테스트");
        verify(passwordEncoder).encode("Password123!");
        verify(memberMapper).insertMember(any(Member.class));
    }

    @Test
    @DisplayName("중복된 이메일로는 회원가입할 수 없다.")
    void signupFailDuplicatedEmail() {
        MemberSignupRequest request = new MemberSignupRequest();
        ReflectionTestUtils.setField(request, "email", "test@example.com");
        ReflectionTestUtils.setField(request, "password", "Password123!");
        ReflectionTestUtils.setField(request, "nickname", "테스트");

        given(memberMapper.existByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.signUp(request))
                .isInstanceOf(MemberBadRequestException.class)
                .hasMessage(DUPLICATED_EMAIL);
    }
}
