package com.guangge.Interview.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.util.CommonResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final SlidingWindowRateLimiter registerLimiter;
    private final SlidingWindowRateLimiter loginLimiter;
    private final boolean trustForwardedFor;
    private final ObjectMapper objectMapper;

    public IpRateLimitFilter(
            @Value("${app.rate-limit.register-per-hour:10}") int registerPerHour,
            @Value("${app.rate-limit.login-per-10-minutes:30}") int loginPerTenMinutes,
            @Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
            ObjectMapper objectMapper) {
        this.registerLimiter = new SlidingWindowRateLimiter(registerPerHour, Duration.ofHours(1), Clock.systemUTC());
        this.loginLimiter = new SlidingWindowRateLimiter(loginPerTenMinutes, Duration.ofMinutes(10), Clock.systemUTC());
        this.trustForwardedFor = trustForwardedFor;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !("/register".equals(path) || "/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = clientIp(request);
        SlidingWindowRateLimiter limiter = "/register".equals(request.getRequestURI())
                ? registerLimiter : loginLimiter;
        if (!limiter.allow(client)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    CommonResult.failed(HttpStatus.TOO_MANY_REQUESTS.value(), "请求过于频繁，请稍后再试"));
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
