package com.guangge.Interview.practice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 遗留接口（旧版前端时代的 REST 路由）此前完全不做鉴权，
 * 例如 GET /frontend/candidates 未登录即可列出全部候选人。
 *
 * 这里统一要求与 /api/v1 相同的 Bearer Token：不删除任何功能，只是堵住匿名访问。
 * 路径用显式前缀列表，避免误伤 /api/v1 下的新接口。
 */
@Component
public class LegacyApiAuthInterceptor implements HandlerInterceptor {

    private static final List<String> LEGACY_PREFIXES = List.of(
            "/frontend/", "/candidate/", "/interview/", "/resume/", "/fileupload/", "/image/", "/talk");
    private static final String LEGACY_AUDIO_STREAM = "/api/stream-audio";

    private final OwnerResolver owners;

    public LegacyApiAuthInterceptor(OwnerResolver owners) {
        this.owners = owners;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isLegacy(request.getRequestURI())) return true;
        owners.requireOwner(request.getHeader(HttpHeaders.AUTHORIZATION));
        return true;
    }

    static boolean isLegacy(String path) {
        if (path == null) return false;
        return LEGACY_AUDIO_STREAM.equals(path) || LEGACY_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
