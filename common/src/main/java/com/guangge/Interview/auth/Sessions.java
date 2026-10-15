package com.guangge.Interview.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Sessions {
    public static final long SHORT_SESSION = TimeUnit.HOURS.toMillis(12);
    public static final long LONG_SESSION = TimeUnit.HOURS.toMillis(30 * 24);

    public static String loginUser(String name,
                                 boolean rememberMe,
                                 String secret) {
        // 这里原来算出了 duration / maxAge 却**没有传给 getToken**，
        // 于是令牌实际用的是 Sign 里硬编码的 10 分钟有效期。现在按参数生效。
        return loginUser(name, rememberMe ? LONG_SESSION : SHORT_SESSION, secret);
    }

    /** 指定有效期签发令牌（由 auth.token.ttl-hours 配置传入）。 */
    public static String loginUser(String name, long durationMillis, String secret) {
        return Sign.getToken(name, secret, durationMillis);
    }

    public static String getToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) return null;
        Cookie tokenCookie = Arrays.stream(cookies)
                .filter(cookie -> AuthConstant.COOKIE_NAME.equals(cookie.getName()))
                .findAny().orElse(null);
        if (tokenCookie == null) return null;
        return tokenCookie.getValue();
    }

    public static void logout(String externalApex, HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthConstant.COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setDomain(externalApex);
        response.addCookie(cookie);
    }
}
