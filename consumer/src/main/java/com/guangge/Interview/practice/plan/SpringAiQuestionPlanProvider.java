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
                %s
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
                focusLine(request.focusDimensions()),
                request.questionCount());
    }

    /**
     * 短板复练：把训练档案里挑出的弱项翻成一句提示。
     *
     * 维度 key 由后端统一翻译成中文，前端只负责传 key —— 免得同一套标签在两边各写一份、
     * 哪天改了一边另一边悄悄失效。
     */
    static String focusLine(List<String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) return "";
        List<String> labels = dimensions.stream()
                .map(SpringAiQuestionPlanProvider::dimensionLabel)
                .filter(label -> !label.isBlank())
                .toList();
        if (labels.isEmpty()) return "";
        return "本次要重点考察（这些是练习者最近较弱的方面）：" + String.join("、", labels)
                + "\n请在不改变题目总数与题型的前提下，多出不答好就会暴露这些问题的题。";
    }

    private static String dimensionLabel(String key) {
        if (key == null) return "";
        return switch (key.strip()) {
            case "relevance" -> "内容相关性（是否答到点上）";
            case "completeness" -> "回答完整度（要点是否答全）";
            case "professionalism" -> "专业程度（技术细节是否准确）";
            case "starStructure" -> "STAR 结构（讲经历是否按情境-任务-行动-结果）";
            case "logic" -> "回答逻辑（表达是否连贯有条理）";
            default -> "";
        };
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isBlank() ? NO_VALUE : value;
    }
}
