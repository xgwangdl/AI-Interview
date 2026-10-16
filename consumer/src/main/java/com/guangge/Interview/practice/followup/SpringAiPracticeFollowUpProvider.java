package com.guangge.Interview.practice.followup;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 走 Spring AI 的追问实现。 */
@Component
public class SpringAiPracticeFollowUpProvider implements PracticeFollowUpProvider {

    private final ChatClient chatClient;
    private final PracticeFollowUpValidator validator;
    private final String modelName;

    public SpringAiPracticeFollowUpProvider(
            ChatClient.Builder chatClientBuilder,
            PracticeFollowUpValidator validator,
            @Value("classpath:prompt/practice-follow-up-system-prompt.st") Resource systemPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        // 注意 clone：ChatClient.Builder.defaultSystem 会修改自身并返回同一实例（踩过）
        this.chatClient = chatClientBuilder.clone().defaultSystem(systemPrompt).build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public PracticeFollowUpResult followUp(String role, String jobDescription, String question, String answer) {
        String raw = chatClient.prompt()
                .user("""
                        目标岗位：%s
                        岗位描述：%s

                        刚才问的问题是：%s

                        候选人的回答（语音转写，可能有错别字）：
                        %s
                        """.formatted(
                        blank(role), blank(jobDescription), question, answer))
                .call()
                .content();
        return validator.parse(raw);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "（未提供）" : value.strip();
    }
}
