package com.guangge.Interview.practice.rewrite;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.guangge.Interview.util.NumberClaims;

import java.util.Set;

/** 走 Spring AI 的回答改写；与其它模型客户端一样，提示词单独 clone。 */
@Component
public class SpringAiAnswerRewriteProvider implements AnswerRewriteProvider {

    private final ChatClient chatClient;
    private final String modelName;

    public SpringAiAnswerRewriteProvider(
            ChatClient.Builder builder,
            @Value("classpath:prompt/answer-rewrite-system-prompt.st") Resource systemPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        this.chatClient = builder.clone().defaultSystem(systemPrompt).build();
        this.modelName = modelName;
    }

    @Override
    public String rewrite(String role, String jobDescription, String question, String transcript) {
        boolean chinese = transcript != null && transcript.codePoints()
                .anyMatch(code -> code >= 0x4e00 && code <= 0x9fff);
        String language = chinese ? "中文" : "English";
        return chatClient.prompt().user("""
                目标岗位：%s
                岗位描述：%s
                面试题：%s
                输出语言：%s（必须与原回答完全一致，禁止翻译）
                数字约束：%s
                原回答（语音转写，可能有错别字）：
                %s
                """.formatted(blank(role), blank(jobDescription), blank(question), language,
                numericConstraint(transcript), blank(transcript)))
                .call().content();
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "（未提供）" : value.strip();
    }

    static String numericConstraint(String transcript) {
        Set<String> allowed = NumberClaims.arabic(transcript);
        if (allowed.isEmpty()) {
            return "原回答没有任何数字。改写中不得出现任何阿拉伯数字或中文数字/量化表述；"
                    + "不要为了显得具体而补充百分比、年份、次数或金额。";
        }
        return "只允许原回答已经出现的这些数字：" + String.join("、", allowed.stream().sorted().toList())
                + "。不得用阿拉伯数字、中文数字或换算后的等价形式新增其他量化事实。";
    }
}
