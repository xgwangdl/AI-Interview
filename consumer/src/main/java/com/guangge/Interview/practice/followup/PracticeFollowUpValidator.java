package com.guangge.Interview.practice.followup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.util.JsonText;
import org.springframework.stereotype.Component;

/**
 * 校验"要不要追问"的输出。
 *
 * 追问是可选动作：模型给 `null`、或者给一个空串，都算"不追问"，不是格式错误 ——
 * 关键是不能把异常当成"必须追问"，否则每道题都会硬凑一个问题出来。
 */
@Component
public class PracticeFollowUpValidator {

    private static final int MAX_FOLLOW_UP_CHARS = 60;
    private static final int MAX_REASON_CHARS = 200;

    private final ObjectMapper objectMapper;

    public PracticeFollowUpValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PracticeFollowUpResult parse(String raw) {
        if (raw == null || raw.isBlank()) throw new PracticeFollowUpException("模型返回为空");
        PracticeFollowUpResult result;
        try {
            result = objectMapper.readValue(JsonText.stripCodeFence(raw), PracticeFollowUpResult.class);
        } catch (Exception exception) {
            throw new PracticeFollowUpException("模型返回的不是约定的 JSON：" + exception.getMessage(), exception);
        }
        return validate(result);
    }

    public PracticeFollowUpResult validate(PracticeFollowUpResult result) {
        if (result == null) throw new PracticeFollowUpException("追问结果缺失");
        String followUp = normalize(result.followUp());
        if (followUp != null && followUp.length() > MAX_FOLLOW_UP_CHARS) {
            // 太长就截断而不是失败：追问啰嗦是体验问题，不值得让整次答题流程报错
            followUp = followUp.substring(0, MAX_FOLLOW_UP_CHARS);
        }
        String reason = normalize(result.reason());
        if (reason != null && reason.length() > MAX_REASON_CHARS) reason = reason.substring(0, MAX_REASON_CHARS);
        return new PracticeFollowUpResult(followUp, reason);
    }

    /** null / "" / "null" / "none" 都当"不追问"。 */
    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }
}
