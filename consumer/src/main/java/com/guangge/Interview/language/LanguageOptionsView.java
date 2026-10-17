package com.guangge.Interview.language;

import java.util.List;

/**
 * 前端开场要的全部选项。
 *
 * {@code serverVoice=false} 不是"暂时没配"，而是**真的没有可用音色**（日语当前就是这种情况）：
 * 前端据此走浏览器语音，并在本机也没有对应语音时降级成纯文字。
 */
public record LanguageOptionsView(
        List<LanguageOption> languages,
        List<LevelOption> levels,
        int defaultMaxTurns,
        int maxTurns) {

    public record LanguageOption(
            String code,
            String displayName,
            String browserVoice,
            boolean serverVoice,
            String note,
            List<Scenario> scenarios) { }

    public record Scenario(String id, String title, String hint, String goal, String opening) { }

    public record LevelOption(String key, String label) { }

    static LanguageOptionsView from(LanguageCatalog catalog) {
        List<LanguageOption> languages = catalog.languages().stream()
                .map(language -> new LanguageOption(
                        language.code(), language.displayName(), language.browserVoice(), language.serverVoice(),
                        language.note(),
                        catalog.scenarios(language.code()).stream()
                                .map(scenario -> new Scenario(scenario.id(), scenario.title(), scenario.chineseHint(),
                                        scenario.goal(), scenario.opening()))
                                .toList()))
                .toList();
        List<LevelOption> levels = java.util.stream.IntStream.range(0, LanguageCatalog.LEVELS.size())
                .mapToObj(index -> new LevelOption(LanguageCatalog.LEVELS.get(index),
                        LanguageCatalog.LEVEL_LABELS.get(index)))
                .toList();
        return new LanguageOptionsView(languages, levels,
                LanguageCatalog.DEFAULT_MAX_TURNS, LanguageCatalog.MAX_TURNS_LIMIT);
    }
}
