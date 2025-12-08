package com.ticketing.global.security.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final AuthenticationFailureHandler customAuthenticationFailureHandler;
    private final AccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(header -> header.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/", "/api/v1/members/**", "/api/v1/concerts/**").permitAll()
                        .requestMatchers("/admin").hasAnyRole("ADMIN")
                        .anyRequest().authenticated()
                );

        http
                .formLogin(login -> login
                        .loginProcessingUrl("/api/v1/members/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                        .permitAll()
                );

        http
                .logout(logout -> logout
                        .logoutUrl("/api/v1/members/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        http
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler(customAccessDeniedHandler)
                );

        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)               // 동시 접속 1명 제한
                        .maxSessionsPreventsLogin(false)  // false: 기존 유저 튕겨냄 (true: 신규 로그인 차단)
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
