package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeOptimizationResult;
import com.guangge.Interview.util.JsonText;
import com.guangge.Interview.util.NumberClaims;
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

    public ResumeOptimizationValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        return validate(result, groundedSource);
    }

    public ResumeOptimizationResult validate(ResumeOptimizationResult result) {
        return validate(result, null);
    }

    public ResumeOptimizationResult validate(ResumeOptimizationResult result, String groundedSource) {
        if (result == null) throw new ResumeFormatException("优化结果缺失");
        if (result.sections() == null || result.sections().isEmpty()) {
            throw new ResumeFormatException("sections 不能为空");
        }
        if (result.sections().size() > MAX_SECTIONS) {
            throw new ResumeFormatException("sections 过多（" + result.sections().size() + "）");
        }

        // 只在**正文**里找占位符。之前把"声明的占位符"也塞进了这个集合，
        // 于是"声明了却没写"这种自相矛盾永远检测不出来（声明的字符串自己就能匹配正则）。
        List<String> inText = new ArrayList<>();
        StringBuilder generated = new StringBuilder();
        for (int index = 0; index < result.sections().size(); index++) {
            ResumeOptimizationResult.Section section = result.sections().get(index);
            String where = "sections[" + index + "]";
            if (section == null) throw new ResumeFormatException(where + " 不能为空");
            if (section.optimized() == null || section.optimized().isBlank()) {
                throw new ResumeFormatException(where + ".optimized 不能为空");
            }
            if (section.changeType() == null
                    || !ResumeOptimizationResult.Section.CHANGE_TYPES.contains(section.changeType())) {
                throw new ResumeFormatException(where + ".changeType 非法：" + section.changeType());
            }
            collectPlaceholders(section.optimized(), inText);
            generated.append(withoutPlaceholders(section.optimized())).append('\n');
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
        return result;
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
