package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeDraftResult;
import com.guangge.Interview.util.JsonText;
import com.guangge.Interview.util.NumberClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验"从零创建"的模型输出。
 *
 * 与优化链路同一条底线：**不许编造**。所以占位符不采信模型的自述，
 * 而是从正文里重新抽（{@link ResumeOptimizationValidator#placeholdersIn}）；
 * 模型声称留了占位符、正文里却一个都没有时，直接判为格式错误让它重来 ——
 * 那正是"嘴上说不编造、实际编了"的典型形状。
 */
@Component
public class ResumeDraftValidator {

    private static final Logger log = LoggerFactory.getLogger(ResumeDraftValidator.class);
    private static final int MAX_QUESTIONS = 3;
    private static final int MAX_RESUME_CHARS = 20000;
    private static final int MAX_QUESTION_CHARS = 200;
    /** 模型有时会把"没填"写成这些字面量 —— 它们不会被高亮，用户会当正常内容漏过去。 */
    private static final List<String> MISSING_WORDS = List.of("未提供", "N/A", "待补充");

    private final ObjectMapper objectMapper;

    public ResumeDraftValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResumeDraftResult parse(String raw) {
        return parse(raw, null);
    }

    public ResumeDraftResult parse(String raw, String groundedSource) {
        if (raw == null || raw.isBlank()) {
            throw new ResumeFormatException("模型返回为空");
        }
        ResumeDraftResult result;
        try {
            result = objectMapper.readValue(JsonText.stripCodeFence(raw), ResumeDraftResult.class);
        } catch (Exception exception) {
            throw new ResumeFormatException("模型返回的不是约定的 JSON：" + exception.getMessage(), exception);
        }
        return validate(result, groundedSource);
    }

    public ResumeDraftResult validate(ResumeDraftResult result) {
        return validate(result, null);
    }

    public ResumeDraftResult validate(ResumeDraftResult result, String groundedSource) {
        if (result == null) throw new ResumeFormatException("生成结果缺失");
        String text = result.resumeText() == null ? "" : result.resumeText().strip();
        if (text.isEmpty()) throw new ResumeFormatException("resumeText 不能为空");
        if (text.length() > MAX_RESUME_CHARS) {
            throw new ResumeFormatException("生成的正文过长（" + text.length() + " 字，上限 " + MAX_RESUME_CHARS + "）");
        }

        List<String> inText = ResumeOptimizationValidator.placeholdersIn(text);
        for (String placeholder : inText) {
            if (isUninformative(placeholder)) {
                throw new ResumeFormatException("占位符没写清要补什么：" + placeholder
                        + "（应当写成 【请补充：电话】 这样）");
            }
        }
        for (String word : MISSING_WORDS) {
            if (text.contains(word)) {
                throw new ResumeFormatException("正文里出现了「" + word + "」这样的占位文字，"
                        + "缺内容必须写成 【请补充：…】");
            }
        }

        if (groundedSource != null) {
            List<String> unsupported = NumberClaims.unsupported(
                            ResumeOptimizationValidator.withoutPlaceholders(text),
                            ResumeOptimizationValidator.withoutPlaceholders(groundedSource))
                    .stream().toList();
            if (!unsupported.isEmpty()) {
                throw new ResumeFormatException("生成的简历出现了素材里没有的数字："
                        + String.join("、", unsupported)
                        + "。真实数据请写成 【请补充：…】 留给用户填写");
            }
        }

        List<String> declared = clean(result.placeholders());
        if (!declared.isEmpty() && inText.isEmpty()) {
            throw new ResumeFormatException("placeholders 声明了占位符，但正文里一个都没出现（疑似编造了内容）");
        }

        List<String> questions = clean(result.questions());
        if (questions.size() > MAX_QUESTIONS) {
            // 多出来的直接砍掉：追问条数是体验问题，不值得让整次生成失败
            log.info("模型给了 {} 条追问，只保留前 {} 条", questions.size(), MAX_QUESTIONS);
            questions = new ArrayList<>(questions.subList(0, MAX_QUESTIONS));
        }
        return new ResumeDraftResult(text, questions, inText);
    }

    /** 去掉空白项与超长项，且不改变顺序。 */
    private static List<String> clean(List<String> values) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) return cleaned;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String trimmed = value.strip();
            cleaned.add(trimmed.length() > MAX_QUESTION_CHARS
                    ? trimmed.substring(0, MAX_QUESTION_CHARS) : trimmed);
        }
        return cleaned;
    }

    /** `【请补充：…】` 这种没信息量的占位符没用：用户看不出该补什么。 */
    private static boolean isUninformative(String placeholder) {
        String body = placeholder.substring(1, placeholder.length() - 1)
                .replace("请补充", "").replace("补充", "")
                .replaceAll("[\\s：:…\\.\\?？!！\\-_—]+", "");
        return body.isEmpty();
    }
}
