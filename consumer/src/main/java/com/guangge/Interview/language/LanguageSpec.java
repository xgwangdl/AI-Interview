package com.guangge.Interview.language;

/**
 * 一门语言需要的全部配置。加一门语言 = 在 {@link LanguageCatalog} 里加一条，不改会话逻辑（FR-12.20）。
 *
 * @param asrHint       识别时的 language_hints（paraformer-realtime-v2 支持 zh / en / ja）
 * @param ttsModel      服务端合成用的模型；null 表示这门语言没有可用音色
 * @param ttsVoice      模型里的音色参数；与 ttsModel 一起为空时走浏览器语音
 * @param browserVoice  浏览器语音的 BCP-47 标签（如 ja-JP），用于 Web Speech API 检索
 * @param serverVoice   服务端是否真有可用音色（探针验证过的才写 true，见 docs/LANGUAGE-PRACTICE-REQUIREMENTS.md §1）
 * @param note          给用户看的一句话说明（为什么走浏览器语音）
 */
public record LanguageSpec(
        String code,
        String displayName,
        String asrHint,
        String ttsModel,
        String ttsVoice,
        String browserVoice,
        boolean serverVoice,
        String note) {
}
