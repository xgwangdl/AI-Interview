package com.guangge.Interview.exception;

import com.guangge.Interview.util.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestExceptionAdviceTest {

    private final RestExceptionAdvice advice = new RestExceptionAdvice();

    @Test
    void mapsForbiddenCodeToHttp403AndKeepsTheMessage() {
        ResponseEntity<CommonResult<Void>> response =
                advice.handleRestException(new RestException("403", "用户名或密码错误"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403L, response.getBody().getCode());
        assertEquals("用户名或密码错误", response.getBody().getMessage());
    }

    @Test
    void mapsInternalErrorCodeToHttp500() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                advice.handleRestException(new RestException("500", "操作失败")).getStatusCode());
    }

    @Test
    void fallsBackToBadRequestForNonNumericCodes() {
        // Sign 抛出的是 "00X"，不是合法的 HTTP 状态码。
        assertEquals(HttpStatus.BAD_REQUEST,
                advice.handleRestException(new RestException("00X", "No signing token!")).getStatusCode());
    }

    @Test
    void turnsResponseStatusExceptionsIntoTheSameErrorBody() {
        ResponseEntity<CommonResult<Void>> response = advice.handleResponseStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 出题服务暂不可用"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503L, response.getBody().getCode());
        assertEquals("AI 出题服务暂不可用", response.getBody().getMessage());
    }
}
