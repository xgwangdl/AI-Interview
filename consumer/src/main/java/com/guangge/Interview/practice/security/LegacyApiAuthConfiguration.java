package com.guangge.Interview.practice.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LegacyApiAuthConfiguration implements WebMvcConfigurer {

    private final LegacyApiAuthInterceptor legacyApiAuth;

    public LegacyApiAuthConfiguration(LegacyApiAuthInterceptor legacyApiAuth) {
        this.legacyApiAuth = legacyApiAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(legacyApiAuth).addPathPatterns("/**");
    }
}
