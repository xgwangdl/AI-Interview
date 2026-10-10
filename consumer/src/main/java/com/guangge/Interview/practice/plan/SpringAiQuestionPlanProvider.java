package com.guangge.Interview.practice.plan;

import com.guangge.Interview.practice.dto.PracticeQuestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/** 用 Spring AI 依据岗位、JD 与简历文件名生成逐题提问。 */
@Component
public class SpringAiQuestionPlanProvider implements QuestionPlanProvider {

    private static final String PROVIDER = "spring-ai";
    private static final String PROMPT_VERSION = "practice-question-plan-v1";
    private static final String NO_VALUE = "（未提供）";

    private final ChatClient chatClient;
    private final QuestionPlanValidator validator;
    private final String modelName;

    public SpringAiQuestionPlanProvider(
            ChatClient.Builder chatClientBuilder,
            QuestionPlanValidator validator,
            @Value("classpath:prompt/practice-question-plan-system-prompt.st") Resource systemPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public List<PracticeQuestion> plan(QuestionPlanRequest request) {
        String raw = chatClient.prompt()
                .user(userMessage(request))
                .call()
                .content();
        return validator.parse(raw, request.questionCount());
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return modelName;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    private static String userMessage(QuestionPlanRequest request) {
        return """
                目标岗位：%s
                目标公司：%s
                岗位描述 JD：%s
                简历文件：%s
                简历内容摘要：%s
                面试类型：%s
                难度：%s
                面试官风格：%s
                请生成 %d 道面试题。
                """.formatted(
                orPlaceholder(request.role()),
                orPlaceholder(request.company()),
                orPlaceholder(request.jobDescription()),
                orPlaceholder(request.resumeName()),
                orPlaceholder(request.resumeText()),
                orPlaceholder(request.interviewType()),
                orPlaceholder(request.difficulty()),
                orPlaceholder(request.interviewerStyle()),
                request.questionCount());
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isBlank() ? NO_VALUE : value;
    }
}
