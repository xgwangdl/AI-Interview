package com.guangge.Interview.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 注册时用户自设的口令用 BCrypt 存哈希。
 *
 * 只引入 spring-security-crypto（不引完整 Spring Security），因为项目用的是自定义 JWT 鉴权，
 * 不需要过滤器链。
 */
@Configuration
public class PasswordEncoderConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
