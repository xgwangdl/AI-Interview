package com.guangge.Interview.resume;

import com.guangge.Interview.resume.dto.ResumeDraftRequests;
import com.guangge.Interview.resume.dto.ResumeDraftResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 走 Spring AI 的"从零创建"实现。 */
@Component
public class SpringAiResumeDraftProvider implements ResumeDraftProvider {

    /**
     * 素材里"用户没填"的标记。刻意写成一句指令而不是"未提供"三个字：
     * 早先版本只写"未提供"，模型就把它原样抄进了简历正文（不会被高亮，用户会漏看）。
     */
    private static final String MISSING = "（未提供 → 正文里必须写成 【请补充：…】）";

    private final ChatClient chatClient;
    private final ResumeDraftValidator validator;
    private final String modelName;

    public SpringAiResumeDraftProvider(
            ChatClient.Builder chatClientBuilder,
            ResumeDraftValidator validator,
            @Value("classpath:prompt/resume-draft-system-prompt.st") Resource systemPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        // 注意 clone：ChatClient.Builder.defaultSystem 会修改自身并返回同一实例，
        // 直接复用注入的 builder 会把别的组件的系统提示词覆盖掉（踩过）。
        this.chatClient = chatClientBuilder.clone().defaultSystem(systemPrompt).build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public ResumeDraftResult draft(ResumeDraftRequests.Draft materials) {
        String raw = chatClient.prompt()
                .user("""
                        用户提供的素材如下（没写的就是没有，不要编造）：

                        %s
                        """.formatted(describe(materials)))
                .call()
                .content();
        return validator.parse(raw);
    }

    @Override
    public ResumeDraftResult refine(String currentText, String supplement, List<String> openQuestions) {
        String questions = openQuestions == null || openQuestions.isEmpty()
                ? "（无）" : "- " + String.join("\n- ", openQuestions);
        String raw = chatClient.prompt()
                .user("""
                        这是现有简历正文：

                        %s

                        之前追问过的待补充项：
                        %s

                        用户这次补充的信息（请并进正文的对应位置，并消掉已经补上的占位符）：

                        %s

                        请输出完整的新正文，不要只输出改动的那几行。
                        """.formatted(currentText, questions, supplement))
                .call()
                .content();
        return validator.parse(raw);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    /** 把素材写成模型好读的清单；空的地方写明"未提供"，免得模型自己脑补。 */
    private static String describe(ResumeDraftRequests.Draft materials) {
        if (materials == null) return "（用户什么都没填）";
        List<String> lines = new ArrayList<>();
        lines.add("目标岗位：" + blankTo(materials.targetRole(), MISSING));

        ResumeDraftRequests.Basics basics = materials.basics();
        if (basics != null) {
            lines.add("姓名：" + blankTo(basics.name(), MISSING));
            lines.add("电话：" + blankTo(basics.phone(), MISSING)
                    + "｜邮箱：" + blankTo(basics.email(), MISSING)
                    + "｜城市：" + blankTo(basics.city(), MISSING));
            if (notBlank(basics.extra())) lines.add("基本信息补充：" + basics.extra().strip());
        }

        appendEntries(lines, "教育背景", materials.education());
        appendEntries(lines, "工作经历", materials.work());
        appendEntries(lines, "项目经历", materials.projects());
        lines.add("掌握的技能：" + blankTo(materials.skills(), MISSING));
        lines.add("其他（获奖 / 证书 / 自我评价）：" + blankTo(materials.other(), MISSING));
        return String.join("\n", lines);
    }

    private static void appendEntries(List<String> lines, String label, List<ResumeDraftRequests.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            lines.add(label + "：" + MISSING);
            return;
        }
        lines.add(label + "：");
        int index = 0;
        for (ResumeDraftRequests.Entry entry : entries) {
            if (entry == null || !hasContent(entry)) continue;
            index++;
            lines.add("  " + index + ") 名称：" + blankTo(entry.title(), MISSING)
                    + "｜角色：" + blankTo(entry.role(), MISSING)
                    + "｜时间：" + blankTo(entry.period(), MISSING));
            if (notBlank(entry.detail())) lines.add("     做了什么：" + entry.detail().strip());
            if (notBlank(entry.result())) lines.add("     结果：" + entry.result().strip());
        }
        if (index == 0) lines.add("  （这一项用户跳过了）");
    }

    private static boolean hasContent(ResumeDraftRequests.Entry entry) {
        return notBlank(entry.title()) || notBlank(entry.role()) || notBlank(entry.period())
                || notBlank(entry.detail()) || notBlank(entry.result());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankTo(String value, String fallback) {
        return notBlank(value) ? value.strip() : fallback;
    }
}
