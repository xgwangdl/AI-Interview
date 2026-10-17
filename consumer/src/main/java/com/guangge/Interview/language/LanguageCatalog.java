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
 *   所以日语走浏览器语音（`speechSynthesis`），服务端不出音频；
 * - 日语的**识别**是好的：真人发音 `ありがとう` / `アイスクリーム` 用 `ja` hint 都能正确识别。
 */
@Component
public class LanguageCatalog {

    public static final String ENGLISH = "en";
    public static final String JAPANESE = "ja";
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
        LANGUAGES.put(JAPANESE, new LanguageSpec(
                JAPANESE, "日本語", "ja", null, null, "ja-JP", false,
                "浏览器语音：DashScope 现有音色会把日语按中文读音念；本机没有日语语音时下一轮只能看文字"));

        SCENARIOS.put(ENGLISH, Map.of(
                "interview", new LanguageScenario("interview", "面试",
                        "面试官会先让你介绍最近的项目，然后顺着你的回答追问细节。",
                        "用英语讲清一个项目：背景、你的动作、结果。",
                        "Thanks for joining today. Could you start by walking me through your most recent project?"),
                "standup", new LanguageScenario("standup", "工作沟通",
                        "像每日站会一样汇报：昨天做了什么、今天计划什么、有没有阻塞。",
                        "用 3 句话完成一次站会汇报，并说清一个阻塞。",
                        "Morning! Let's do a quick stand-up. What did you get done yesterday, and is anything blocking you?"),
                "daily", new LanguageScenario("daily", "日常口语",
                        "咖啡店点单：说清要什么、杯型、要不要堂食。",
                        "完成一次点单，并回答店员的一个追问。",
                        "Hi there, welcome in! What can I get for you today?")));
        SCENARIOS.put(JAPANESE, Map.of(
                "interview", new LanguageScenario("interview", "面试",
                        "面试官会用敬语提问，先自我介绍，再讲一个项目。",
                        "用日语完成 30 秒自我介绍，并说明一次团队协作。",
                        "本日はお時間をいただきありがとうございます。まず、簡単に自己紹介をお願いできますか。"),
                "standup", new LanguageScenario("standup", "工作沟通",
                        "像朝会一样汇报昨天、今天和阻塞，注意用敬体。",
                        "用 3 句话完成一次朝会汇报，并说明一个风险。",
                        "おはようございます。昨日やったことと、今日の予定を簡単に共有していただけますか。"),
                "daily", new LanguageScenario("daily", "日常口语",
                        "在便利店点单：说清要什么、要不要加热。",
                        "完成一次点单，并回答店员的一个追问。",
                        "いらっしゃいませ。ご注文はお決まりですか。")));
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
