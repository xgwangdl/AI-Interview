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
    public PracticeFollowUpResult followUp(PracticeFollowUpContext context) {
        String raw = chatClient.prompt()
                .user("""
                        目标岗位：%s
                        岗位描述：%s

                        这一题的原问题是：%s

                        到目前为止关于这道题的问答（回答是语音转写，可能有错别字）：
                        %s

                        这是第 %d 次追问。如果这是第二次追问，说明上一轮追问还是没问出细节 ——
                        那就换一个角度（要一个数字、一次取舍、或者最后的结果），不要再重复上一个问法。
                        """.formatted(
                        blank(context.role()), blank(context.jobDescription()),
                        blank(context.rootQuestion()), context.renderTurns(), context.round()))
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
