package com.ticketing.global.security.service;

import com.ticketing.infrastructure.persistence.member.MemberMapper;
import com.ticketing.domain.member.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static com.ticketing.domain.member.exception.MemberErrorMessage.NOT_EXIST_MEMBER;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberMapper memberMapper;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberMapper.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(NOT_EXIST_MEMBER));
        SessionMember sessionMember = SessionMember.create(member.getMemberId(), member.getEmail(), member.getNickname());
        String role = member.getRole() == null ? Member.DEFAULT_ROLE : member.getRole();
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return new CustomUserDetails(
                sessionMember,
                member.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority(authority))
        );
    }
}
