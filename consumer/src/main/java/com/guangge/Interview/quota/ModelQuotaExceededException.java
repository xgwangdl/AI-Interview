package com.guangge.Interview.quota;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 模型额度用完了（每日上限或并发上限）。
 *
 * 用 429 而不是 500：这不是故障，是护栏在按预期工作，
 * 前端据此显示"明天恢复"，而不是弹一句"服务器错误"。
 */
public class ModelQuotaExceededException extends ResponseStatusException {

    public ModelQuotaExceededException(String reason) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
    }
}
