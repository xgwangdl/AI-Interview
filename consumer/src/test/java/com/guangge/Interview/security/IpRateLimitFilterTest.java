package com.guangge.Interview.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpRateLimitFilterTest {

    @Test
    void limitsRegistrationByClientIp() throws Exception {
        IpRateLimitFilter filter = new IpRateLimitFilter(2, 2, false, new ObjectMapper());
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();

        MockHttpServletResponse first = request(filter, chain);
        MockHttpServletResponse second = request(filter, chain);
        MockHttpServletResponse blocked = request(filter, chain);

        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus());
        assertEquals(429, blocked.getStatus());
        assertEquals(2, passed.get());
    }

    private static MockHttpServletResponse request(IpRateLimitFilter filter, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/register");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
