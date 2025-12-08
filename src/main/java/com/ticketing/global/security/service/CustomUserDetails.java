package com.ticketing.global.security.service;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Getter
public class CustomUserDetails implements UserDetails {

    private final SessionMember sessionMember;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(SessionMember sessionMember, String password, Collection<? extends GrantedAuthority> authorities) {
        this.sessionMember = sessionMember;
        this.password = password;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return sessionMember.getEmail();
    }
}
