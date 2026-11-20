package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeOptimizationResult;
import com.guangge.Interview.util.JsonText;
import com.guangge.Interview.util.NumberClaims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验模型的优化结果。
 *
 * 最要紧的一条：**占位符必须和实际文本对得上** ——
 * 否则模型嘴上说"我没有编造、缺的地方留了占位"，结果正文里塞了凭空数字，用户根本看不出来。
 */
@Component
public class ResumeOptimizationValidator {

    /** 占位符形如 【请补充：订单量级】 */
    static final Pattern PLACEHOLDER = Pattern.compile("【[^】]{1,60}】");
    private static final int MAX_SECTIONS = 60;

    private final ObjectMapper objectMapper;
    private final ResumeDataValidator resumeDataValidator;

    public ResumeOptimizationValidator(ObjectMapper objectMapper) {
        this(objectMapper, new ResumeDataValidator(objectMapper));
    }

    @Autowired
    public ResumeOptimizationValidator(ObjectMapper objectMapper, ResumeDataValidator resumeDataValidator) {
        this.objectMapper = objectMapper;
        this.resumeDataValidator = resumeDataValidator;
    }

    public ResumeOptimizationResult parse(String raw) {
        return parse(raw, null);
    }

    public ResumeOptimizationResult parse(String raw, String groundedSource) {
        if (raw == null || raw.isBlank()) {
            throw new ResumeFormatException("模型返回为空");
        }
        ResumeOptimizationResult result;
        try {
            result = objectMapper.readValue(JsonText.stripCodeFence(raw), ResumeOptimizationResult.class);
        } catch (Exception exception) {
            throw new ResumeFormatException("模型返回的不是约定的 JSON：" + exception.getMessage(), exception);
        }
        return validate(result, groundedSource, true);
    }

    public ResumeOptimizationResult validate(ResumeOptimizationResult result) {
        return validate(result, null, false);
    }

    public ResumeOptimizationResult validate(ResumeOptimizationResult result, String groundedSource) {
        return validate(result, groundedSource, false);
    }

    private ResumeOptimizationResult validate(
            ResumeOptimizationResult result, String groundedSource, boolean requireResumeData) {
        if (result == null) throw new ResumeFormatException("优化结果缺失");
        if (requireResumeData && result.resumeData() == null) {
            throw new ResumeFormatException("resumeData 不能为空");
        }
        if (result.resumeData() != null) {
            resumeDataValidator.validate(result.resumeData(), groundedSource);
        }
        List<ResumeOptimizationResult.Section> sections =
                result.sections() == null ? List.of() : result.sections();
        if (sections.isEmpty() && result.resumeData() == null) {
            throw new ResumeFormatException("sections 不能为空");
        }
        if (sections.size() > MAX_SECTIONS) {
            throw new ResumeFormatException("sections 过多（" + sections.size() + "）");
        }

        // 只在**正文**里找占位符。之前把"声明的占位符"也塞进了这个集合，
        // 于是"声明了却没写"这种自相矛盾永远检测不出来（声明的字符串自己就能匹配正则）。
        List<String> inText = new ArrayList<>();
        StringBuilder generated = new StringBuilder();
        List<ResumeOptimizationResult.Section> normalized = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            ResumeOptimizationResult.Section section = sections.get(index);
            String where = "sections[" + index + "]";
            if (section == null) continue;
            String optimized = section.optimized();
            String changeType = section.changeType();
            if (optimized == null || optimized.isBlank()) {
                // A "keep" patch often omits optimized text because the original is unchanged.
                // If both fields are empty, the model returned a pure noise object. The structured
                // resumeData remains authoritative, so do not fail an otherwise usable optimization.
                if (section.original() == null || section.original().isBlank()) {
                    continue;
                }
                optimized = section.original();
                changeType = "keep";
            }
            if (changeType == null
                    || !ResumeOptimizationResult.Section.CHANGE_TYPES.contains(changeType)) {
                throw new ResumeFormatException(where + ".changeType 非法：" + changeType);
            }
            ResumeOptimizationResult.Section value = new ResumeOptimizationResult.Section(
                    section.kind(), section.original(), optimized, section.reason(), changeType);
            normalized.add(value);
            collectPlaceholders(optimized, inText);
            generated.append(withoutPlaceholders(optimized)).append('\n');
        }
        if (normalized.isEmpty() && result.resumeData() == null) {
            throw new ResumeFormatException("sections 不能为空");
        }

        if (groundedSource != null) {
            List<String> unsupported = NumberClaims.unsupported(generated.toString(), groundedSource)
                    .stream().toList();
            if (!unsupported.isEmpty()) {
                throw new ResumeFormatException("优化后的简历出现了原文没有的数字："
                        + String.join("、", unsupported)
                        + "。真实数据请让用户补充，不要由模型编写");
            }
        }

        List<String> declared = result.placeholders() == null ? List.of() : result.placeholders();
        // 声明了占位符、正文里却一个都没有 → 模型只是嘴上说"我没编造"，实际没留占位，拦下来
        boolean anyDeclaredInText = declared.stream().anyMatch(inText::contains);
        if (!declared.isEmpty() && !anyDeclaredInText) {
            throw new ResumeFormatException(
                    "placeholders 声明了占位符，但正文里一个都没出现（疑似编造了数据）");
        }
        return new ResumeOptimizationResult(
                result.resumeData(),
                result.summary(),
                List.copyOf(normalized),
                result.placeholders(),
                result.keywordCoverage());
    }

    private static void collectPlaceholders(String text, List<String> sink) {
        if (text == null) return;
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (!sink.contains(value)) sink.add(value);
        }
    }

    /** 从正文里抽出现实存在的占位符（前端据此高亮，不信任模型的自述）。 */
    public static List<String> placeholdersIn(String text) {
        List<String> found = new ArrayList<>();
        collectPlaceholders(text, found);
        return found;
    }

    public static String withoutPlaceholders(String text) {
        return text == null ? "" : PLACEHOLDER.matcher(text).replaceAll("");
    }
}
