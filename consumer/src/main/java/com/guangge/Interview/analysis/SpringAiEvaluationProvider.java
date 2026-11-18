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
                面试类型：%s
                题目类型：%s
                评分重点：%s
                面试问题（%s）：%s
                回答时长（秒）：%s
                候选人回答转写：
                %s
                """.formatted(
                orPlaceholder(request.role()),
                orPlaceholder(request.jobDescription()),
                orPlaceholder(request.interviewType()),
                orPlaceholder(request.questionCategory()),
                rubricFocus(request.questionCategory()),
                orPlaceholder(request.questionId()),
                orPlaceholder(request.questionText()),
                request.durationSeconds() == null ? "未知" : request.durationSeconds().toString(),
                orPlaceholder(request.transcript()));
    }

    private static String rubricFocus(String category) {
        if (category == null) return "按通用面试回答标准评价，重点看是否切题、完整、具体、有逻辑。";
        return switch (category) {
            case "behavioral" -> "重点检查情境、任务、个人行动、结果和反思，区分团队成果与个人贡献。";
            case "technical", "java", "spring" ->
                    "重点检查技术正确性、边界条件、失败处理、性能和方案取舍。";
            case "system_design", "design" ->
                    "重点检查需求约束、容量估算、数据模型、扩展性、故障恢复和监控。";
            case "follow_up" -> "重点检查是否正面回答追问，并补充上一轮回答缺失的具体信息。";
            default -> "按通用面试回答标准评价，重点看是否切题、完整、具体、有逻辑。";
        };
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isBlank() ? "（未提供）" : value;
    }
}
