package com.guangge.Interview.practice.plan;

import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionPlanService {

    private static final Logger log = LoggerFactory.getLogger(QuestionPlanService.class);

    private final QuestionPlanProvider provider;

    public QuestionPlanService(QuestionPlanProvider provider) {
        this.provider = provider;
    }

    /**
     * 生成逐题提问。失败时抛 503，客户端据此回退到内置题库，不会卡住练习创建。
     */
    public QuestionPlanView plan(QuestionPlanRequest request) {
        try {
            return new QuestionPlanView(
                    provider.provider(),
                    provider.model(),
                    provider.promptVersion(),
                    provider.plan(request));
        } catch (RuntimeException exception) {
            log.warn("AI 出题失败，客户端将回退到内置题库: {}", LogSanitizer.sanitize(exception.getMessage()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 出题服务暂不可用");
        }
    }
}
