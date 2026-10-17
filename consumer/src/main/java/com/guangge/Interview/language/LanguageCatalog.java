package com.guangge.Interview.language;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 语言、场景、等级的注册表。
 *
 * 音色不是拍脑袋定的，来自 2026-10-17 的真实探针（`VoiceCapabilityProbe`）：
 * - 英语：cosyvoice-v2 合成英文后送 ASR 往返能识别出 billing / latency，可用；
 * - 日语：**所有可用 DashScope 音色都把日语按中文读音念**（识别回来是"瞿影先献基叔…"），
 *   实际试用后用户反馈"完全不可用"，所以**暂时下线**。代码结构保留（加回 ja 的配置即可），
 *   但上线前必须先解决日语服务端音色（或确认浏览器语音可用）。
 */
@Component
public class LanguageCatalog {

    public static final String ENGLISH = "en";
    public static final int DEFAULT_MAX_TURNS = 8;
    public static final int MAX_TURNS_LIMIT = 20;
    public static final List<String> LEVELS = List.of("beginner", "intermediate", "advanced");
    public static final List<String> LEVEL_LABELS = List.of("初级", "中级", "高级");

    private static final Map<String, LanguageSpec> LANGUAGES = new LinkedHashMap<>();
    private static final Map<String, Map<String, LanguageScenario>> SCENARIOS = new LinkedHashMap<>();

    static {
        LANGUAGES.put(ENGLISH, new LanguageSpec(
                ENGLISH, "English", "en", "cosyvoice-v2", "longxiaochun_v2", "en-US", true,
                "服务端音色（cosyvoice-v2），可直接在小人身上播放"));
        // 用 LinkedHashMap 而不是 Map.of：Map.of 的迭代顺序未定义，首页第一个场景会随机变
        Map<String, LanguageScenario> english = new LinkedHashMap<>();
        english.put("interview", new LanguageScenario("interview", "面试",
                "面试官会先让你介绍最近的项目，然后顺着你的回答追问细节。",
                "用英语讲清一个项目：背景、你的动作、结果。",
                "Thanks for joining today. Could you start by walking me through your most recent project?"));
        english.put("standup", new LanguageScenario("standup", "工作沟通",
                "像每日站会一样汇报：昨天做了什么、今天计划什么、有没有阻塞。",
                "用 3 句话完成一次站会汇报，并说清一个阻塞。",
                "Morning! Let's do a quick stand-up. What did you get done yesterday, and is anything blocking you?"));
        english.put("daily", new LanguageScenario("daily", "日常口语",
                "咖啡店点单：说清要什么、杯型、要不要堂食。",
                "完成一次点单，并回答店员的一个追问。",
                "Hi there, welcome in! What can I get for you today?"));
        SCENARIOS.put(ENGLISH, english);
    }

    public Optional<LanguageSpec> language(String code) {
        return Optional.ofNullable(LANGUAGES.get(code));
    }

    public Optional<LanguageScenario> scenario(String language, String scenarioId) {
        Map<String, LanguageScenario> scenarios = SCENARIOS.get(language);
        return scenarios == null ? Optional.empty() : Optional.ofNullable(scenarios.get(scenarioId));
    }

    public List<LanguageSpec> languages() {
        return List.copyOf(LANGUAGES.values());
    }

    public List<LanguageScenario> scenarios(String language) {
        Map<String, LanguageScenario> scenarios = SCENARIOS.get(language);
        return scenarios == null ? List.of() : List.copyOf(scenarios.values());
    }

    /** 等级 → 提示词里的一句话。认不出的等级按中级处理（客户端传错不该让会话打不开）。 */
    public static String levelInstruction(String level) {
        return switch (level == null ? "" : level) {
            case "beginner" -> "学习者水平：初级。只用最常用的 1500 词和短句，语速慢一点，一次只推进一个点。";
            case "advanced" -> "学习者水平：高级。可以用自然语速与习语，回答可以更有信息量。";
            default -> "学习者水平：中级。用常见词和完整句子，避免生僻词。";
        };
    }

    public static String levelLabel(String level) {
        int index = LEVELS.indexOf(level == null ? "" : level);
        return index < 0 ? "中级" : LEVEL_LABELS.get(index);
    }
}
