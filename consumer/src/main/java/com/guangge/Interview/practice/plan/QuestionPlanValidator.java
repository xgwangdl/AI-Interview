package com.guangge.Interview.practice.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.util.JsonText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把模型返回的题目文本收敛成固定结构。
 *
 * 题目 id 由服务端生成，不采信模型给的值，避免重复或非法 id 污染会话内的题目标识。
 */
@Component
public class QuestionPlanValidator {

    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_CATEGORY_LENGTH = 100;
    private static final String DEFAULT_CATEGORY = "综合";

    private final ObjectMapper objectMapper;

    public QuestionPlanValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<PracticeQuestion> parse(String raw, int expectedCount) {
        if (raw == null || raw.isBlank()) {
            throw new QuestionPlanException("模型没有返回题目");
        }
        List<DraftQuestion> drafts;
        try {
            drafts = objectMapper.readValue(JsonText.stripCodeFence(raw), new TypeReference<List<DraftQuestion>>() { });
        } catch (Exception exception) {
            throw new QuestionPlanException("模型返回的不是题目 JSON 数组", exception);
        }
        if (drafts == null || drafts.size() != expectedCount) {
            throw new QuestionPlanException(
                    "模型返回 " + (drafts == null ? 0 : drafts.size()) + " 道题，期望 " + expectedCount + " 道");
        }

        List<PracticeQuestion> questions = new ArrayList<>(drafts.size());
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < drafts.size(); index++) {
            DraftQuestion draft = drafts.get(index);
            if (draft == null || draft.text() == null || draft.text().isBlank()) {
                throw new QuestionPlanException("第 " + (index + 1) + " 道题缺少题干");
            }
            String text = draft.text().trim();
            if (text.length() > MAX_TEXT_LENGTH) {
                throw new QuestionPlanException("第 " + (index + 1) + " 道题题干超过 " + MAX_TEXT_LENGTH + " 字");
            }
            if (!seen.add(text)) {
                throw new QuestionPlanException("模型返回了重复的题目");
            }
            questions.add(new PracticeQuestion("q-" + (index + 1), text, normalizeCategory(draft.category())));
        }
        return List.copyOf(questions);
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return DEFAULT_CATEGORY;
        String trimmed = category.trim();
        return trimmed.length() > MAX_CATEGORY_LENGTH ? trimmed.substring(0, MAX_CATEGORY_LENGTH) : trimmed;
    }

    private record DraftQuestion(String text, String category) { }
}
