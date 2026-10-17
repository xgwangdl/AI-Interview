package com.guangge.Interview.language;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语言注册表：三门以内的语言各自都能只有"配置"就能跑起来。
 *
 * 音色的结论来自 2026-10-17 的真实探针：英语有服务端音色，日语没有（只能用浏览器语音），
 * 但两门语言的**识别**都是可用的。
 */
class LanguageCatalogTest {

    private final LanguageCatalog catalog = new LanguageCatalog();

    @Test
    @DisplayName("英语与日语都在，每门语言三个场景且都有固定开场白")
    void exposesBothLanguagesWithScenarios() {
        assertThat(catalog.languages()).extracting(LanguageSpec::code).containsExactly("en", "ja");
        for (LanguageSpec language : catalog.languages()) {
            assertThat(catalog.scenarios(language.code())).hasSize(3)
                    .allSatisfy(scenario -> {
                        assertThat(scenario.opening()).isNotBlank();
                        assertThat(scenario.goal()).isNotBlank();
                    });
        }
    }

    @Test
    @DisplayName("日语没有服务端音色：走浏览器语音，别拿中文音色硬念")
    void japaneseFallsBackToTheBrowserVoice() {
        LanguageSpec japanese = catalog.language("ja").orElseThrow();
        assertThat(japanese.asrHint()).isEqualTo("ja");
        assertThat(japanese.serverVoice()).isFalse();
        assertThat(japanese.ttsModel()).isNull();
        assertThat(japanese.browserVoice()).isEqualTo("ja-JP");

        LanguageSpec english = catalog.language("en").orElseThrow();
        assertThat(english.serverVoice()).isTrue();
        assertThat(english.ttsModel()).isEqualTo("cosyvoice-v2");
        assertThat(english.browserVoice()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("未知语言/场景返回空，由 Service 转成 400")
    void unknownLanguagesAreEmpty() {
        assertThat(catalog.language("fr")).isEmpty();
        assertThat(catalog.scenario("en", "nope")).isEmpty();
        assertThat(catalog.scenarios("fr")).isEmpty();
    }

    @Test
    @DisplayName("水平提示：认不出的值按中级，不让客户端传错就打不开")
    void levelInstructionsFallBackToIntermediate() {
        assertThat(LanguageCatalog.levelInstruction("beginner")).contains("1500");
        assertThat(LanguageCatalog.levelInstruction("whatever")).contains("中级");
        assertThat(LanguageCatalog.levelLabel("advanced")).isEqualTo("高级");
    }
}
