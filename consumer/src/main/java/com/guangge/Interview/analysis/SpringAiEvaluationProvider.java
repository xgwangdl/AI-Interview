package com.guangge.Interview.analysis;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Evaluation adapter backed by the Spring AI chat client. */
@Component
@ConditionalOnProperty(name = "practice.evaluation.provider", havingValue = "spring-ai", matchIfMissing = true)
public class SpringAiEvaluationProvider implements EvaluationProvider {

    private static final String PROMPT_VERSION = "practice-evaluation-v1";

    private final ChatClient chatClient;
    private final EvaluationResultValidator validator;
    private final String modelName;

    public SpringAiEvaluationProvider(
            ChatClient.Builder chatClientBuilder,
            EvaluationResultValidator validator,
            @Value("classpath:prompt/practice-evaluation-system-prompt.st") Resource systemPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public EvaluationOutcome evaluate(EvaluationRequest request) {
        String raw = chatClient.prompt()
                .user(userMessage(request))
                .call()
                .content();
        return new EvaluationOutcome(validator.parse(raw, request.transcript()), raw);
    }

    @Override
    public EvaluationProvenance provenance() {
        return new EvaluationProvenance(modelName, modelName, PROMPT_VERSION);
    }

    private static String userMessage(EvaluationRequest request) {
        return """
                岗位：%s
                岗位描述：%s
                面试问题（%s）：%s
                回答时长（秒）：%s
                候选人回答转写：
                %s
                """.formatted(
                orPlaceholder(request.role()),
                orPlaceholder(request.jobDescription()),
                orPlaceholder(request.questionId()),
                orPlaceholder(request.questionText()),
                request.durationSeconds() == null ? "未知" : request.durationSeconds().toString(),
                orPlaceholder(request.transcript()));
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isBlank() ? "（未提供）" : value;
    }
}
