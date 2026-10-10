package com.guangge.Interview.exception;

import com.guangge.Interview.util.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * 把业务异常转成带正确状态码的统一响应。
 *
 * 原来这里的 {@code @ExceptionHandler} 被注释掉了，于是所有 RestException 都会冒泡成
 * Spring 默认的 500，前端拿不到 {code, message}，登录失败也只能显示通用错误。
 */
@ControllerAdvice
public class RestExceptionAdvice {
    private static final Logger logger = LoggerFactory.getLogger(RestExceptionAdvice.class);

    @ResponseBody
    @ExceptionHandler(RestException.class)
    public ResponseEntity<CommonResult<Void>> handleRestException(RestException exception) {
        HttpStatus status = resolveStatus(exception.getCode());
        logger.warn("业务异常 code={} status={} message={}",
                exception.getCode(), status.value(), exception.getMessage());
        return ResponseEntity.status(status)
                .body(CommonResult.<Void>failed(status.value(), exception.getMessage()));
    }

    private static HttpStatus resolveStatus(String code) {
        try {
            HttpStatus status = HttpStatus.resolve(Integer.parseInt(code));
            return status == null ? HttpStatus.BAD_REQUEST : status;
        } catch (NumberFormatException notNumeric) {
            // 例如 Sign 抛出的 "00X"：不是 HTTP 状态码，按参数错误处理。
            return HttpStatus.BAD_REQUEST;
        }
    }

    /**
     * 业务接口用 ResponseStatusException 表达 4xx/5xx。
     * 不处理的话 Spring 会返回默认错误体，前端只能把整段 JSON 当作错误消息显示给用户。
     */
    @ResponseBody
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        logger.warn("请求被拒绝 status={} message={}", status.value(), message);
        return ResponseEntity.status(status).body(CommonResult.<Void>failed(status.value(), message));
    }
}
