package com.guangge.Interview.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.coding.dto.JudgeOutcome;
import com.guangge.Interview.util.JsonText;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 简答题评分：唯一没有客观答案的题型，交给模型按参考要点打分。
 *
 * 和评分链路一样强制走 JSON 结构校验——自由文本一律拒绝，避免把不可解析的输出落库。
 */
@Component
public class SpringAiShortAnswerGrader implements ShortAnswerGrader {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiShortAnswerGrader(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value("classpath:prompt/coding-short-answer-prompt.st") Resource systemPrompt) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public JudgeOutcome.ShortAnswerReview grade(String question, String referencePoints, String answer) {
        String raw = chatClient.prompt()
                .user("""
                        题目：%s
                        参考要点：%s
                        候选人回答：
                        %s
                        """.formatted(blankToPlaceholder(question), blankToPlaceholder(referencePoints), answer))
                .call()
                .content();

        JudgeOutcome.ShortAnswerReview review = parse(raw);
        if (review.score() < 0 || review.score() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "简答题评分超出范围");
        }
        return review;
    }

    private JudgeOutcome.ShortAnswerReview parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "简答题评分返回为空");
        }
        try {
            JudgeOutcome.ShortAnswerReview review =
                    objectMapper.readValue(JsonText.stripCodeFence(raw), JudgeOutcome.ShortAnswerReview.class);
            if (review == null || review.score() < 0 || review.summary() == null || review.summary().isBlank()) {
                throw new IllegalArgumentException("score/summary 缺失");
            }
            return new JudgeOutcome.ShortAnswerReview(
                    review.score(),
                    review.summary(),
                    review.matched() == null ? List.of() : review.matched(),
                    review.missing() == null ? List.of() : review.missing());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "简答题评分格式不正确：" + exception.getMessage(), exception);
        }
    }

    private static String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "（未提供）" : value;
    }
}
