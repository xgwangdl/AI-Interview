package com.guangge.Interview.resume.export;

import java.util.Locale;

/**
 * 导出用的强调色。
 *
 * 与前端 `web/src/services/theme.js` 的 THEMES 一一对应：用户在页面上选了哪个主题，
 * 导出的 PDF / Word 就用同一个强调色，两边看起来是同一套东西。
 */
public enum ResumeTheme {

    GREEN("287B63", "1F5F4D"),
    BLUE("2F6B9A", "245578"),
    AMBER("B4622A", "8F4C1F");

    private final String accent;
    private final String accentDark;

    ResumeTheme(String accent, String accentDark) {
        this.accent = accent;
        this.accentDark = accentDark;
    }

    /** 主强调色（标题、色块）。 */
    public String accent() {
        return accent;
    }

    /** 加深一档，用在小节标题文字上，保证在浅色下也够清晰。 */
    public String accentDark() {
        return accentDark;
    }

    public static ResumeTheme parse(String name) {
        if (name == null || name.isBlank()) return GREEN;
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "blue" -> BLUE;
            case "amber" -> AMBER;
            default -> GREEN;
        };
    }
}
