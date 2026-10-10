package com.guangge.Interview.practice.security;

import com.guangge.Interview.auth.Sign;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyApiAuthInterceptorTest {

    private static final String SECRET = "test-secret";

    private final LegacyApiAuthInterceptor interceptor = new LegacyApiAuthInterceptor(new OwnerResolver(SECRET));

    @Test
    void onlyGuardsTheLegacyRoutes() {
        assertTrue(LegacyApiAuthInterceptor.isLegacy("/frontend/candidates"));
        assertTrue(LegacyApiAuthInterceptor.isLegacy("/interview/chat"));
        assertTrue(LegacyApiAuthInterceptor.isLegacy("/api/stream-audio"));

        // 新接口不能被这里拦住：/api/v1/resume/extract 与遗留的 /resume/upload 只差一层前缀。
        assertFalse(LegacyApiAuthInterceptor.isLegacy("/api/v1/resume/extract"));
        assertFalse(LegacyApiAuthInterceptor.isLegacy("/api/v1/practice-sessions"));
        assertFalse(LegacyApiAuthInterceptor.isLegacy("/login"));
    }

    @Test
    void rejectsAnonymousLegacyRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/frontend/candidates");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertTrue(error.getStatusCode().is4xxClientError(), error.getStatusCode().toString());
    }

    @Test
    void allowsLegacyRequestsWithAValidToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/frontend/candidates");
        request.addHeader("Authorization", "Bearer " + Sign.getToken("smoke", SECRET));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void leavesTheNewApiAlone() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resume/extract");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()),
                "新接口应继续由控制器自己校验身份");
    }
}
