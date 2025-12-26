package com.ticketing.global.config;

import com.ticketing.global.interceptor.QueueAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final QueueAccessInterceptor queueAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueAccessInterceptor)
                .addPathPatterns("/api/v1/**/seats/**", "/api/v1/**/payment/**")
                .excludePathPatterns("/api/v1/queue/**");
    }
}