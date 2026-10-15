package com.guangge.Interview.resume.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把简历正文（纯文本）切成渲染用的块。
 *
 * 简历只存正文（需求文档 Q5：一期不做结构化字段），所以导出时按行识别版式：
 * Markdown 标记 &gt; 已知小节名 &gt; 短行带冒号 &gt; 普通段落。
 * 识别错了只影响字号与缩进，正文一个字都不会丢。
 */
public final class ResumeLayout {

    public enum Kind {
        /** 小节标题。 */
        HEADING,
        /** 正文段落。 */
        PARAGRAPH,
        /** 无序列表项。 */
        BULLET
    }

    /** level：1 = 文档标题，2 = 小节，3 = 小节内的子标题；段落与列表固定 0。 */
    public record Block(Kind kind, int level, String text) { }

    /** 渲染片段。placeholder = 需要用户补充的占位符（【请补充：xx】）。 */
    public record Piece(String text, boolean placeholder) { }

    /** 常见小节名，命中即当成小节标题（否则「工作经历」会和正文一个字号）。 */
    private static final Set<String> SECTION_TITLES = Set.of(
            "个人信息", "基本资料", "基本信息", "联系方式", "求职意向", "求职目标",
            "教育背景", "教育经历", "工作经历", "工作经验", "实习经历", "校园经历", "实践经历",
            "项目经历", "项目经验", "项目背景", "专业技能", "技能清单", "技能特长", "职业技能",
            "获奖情况", "荣誉奖项", "证书", "培训经历", "自我评价", "个人评价", "其他");

    private static final int MAX_SECTION_TITLE_LENGTH = 16;
    private static final int MAX_MARKDOWN_HEADING_LEVEL = 6;

    /** 与前端 ResumeDetailView 的高亮规则保持一致。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("【[^】]{1,60}】");

    private ResumeLayout() { }

    public static List<Block> parse(String text) {
        List<Block> blocks = new ArrayList<>();
        if (text == null || text.isBlank()) return blocks;
        for (String raw : text.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            int hashes = markdownHeadingLevel(line);
            if (hashes > 0) {
                blocks.add(new Block(Kind.HEADING, Math.min(hashes + 1, 3), line.substring(hashes).strip()));
                continue;
            }
            if (isBullet(line)) {
                blocks.add(new Block(Kind.BULLET, 0, line.substring(2).strip()));
                continue;
            }
            if (isSectionTitle(line)) {
                blocks.add(new Block(Kind.HEADING, 2, stripTrailingColon(line)));
                continue;
            }
            blocks.add(new Block(Kind.PARAGRAPH, 0, line));
        }
        return blocks;
    }

    /** 按占位符把文本切成片段；占位符与正文分开，渲染时才能区别对待。 */
    public static List<Piece> pieces(String text) {
        List<Piece> pieces = new ArrayList<>();
        if (text == null || text.isEmpty()) return pieces;
        Matcher matcher = PLACEHOLDER.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) pieces.add(new Piece(text.substring(last, matcher.start()), false));
            pieces.add(new Piece(matcher.group(), true));
            last = matcher.end();
        }
        if (last < text.length()) pieces.add(new Piece(text.substring(last), false));
        return pieces;
    }

    /** 转成 Markdown：标题用 `#`，小节用 `##`，列表保持 `-`。 */
    public static String toMarkdown(String title, String text) {
        StringBuilder out = new StringBuilder();
        if (title != null && !title.isBlank()) out.append("# ").append(title.strip()).append("\n\n");
        for (Block block : parse(text)) {
            switch (block.kind()) {
                case HEADING -> out.append("#".repeat(Math.max(2, block.level()))).append(' ')
                        .append(block.text()).append("\n\n");
                case BULLET -> out.append("- ").append(block.text()).append('\n');
                case PARAGRAPH -> out.append(block.text()).append("\n\n");
            }
        }
        return out.toString();
    }

    private static int markdownHeadingLevel(String line) {
        int hashes = 0;
        while (hashes < line.length() && line.charAt(hashes) == '#' && hashes < MAX_MARKDOWN_HEADING_LEVEL) {
            hashes++;
        }
        boolean isHeading = hashes > 0 && hashes < line.length()
                && line.charAt(hashes) == ' ' && !line.substring(hashes).isBlank();
        return isHeading ? hashes : 0;
    }

    private static boolean isBullet(String line) {
        if (line.length() < 2) return false;
        char marker = line.charAt(0);
        boolean supported = marker == '-' || marker == '*' || marker == '•';
        return supported && line.charAt(1) == ' ';
    }

    private static boolean isSectionTitle(String line) {
        String canonical = stripTrailingColon(line);
        if (canonical.isEmpty() || canonical.length() > MAX_SECTION_TITLE_LENGTH) return false;
        if (SECTION_TITLES.contains(canonical)) return true;
        return line.endsWith("：") || line.endsWith(":");
    }

    private static String stripTrailingColon(String line) {
        String value = line.strip();
        if (value.endsWith("：") || value.endsWith(":")) value = value.substring(0, value.length() - 1);
        return value.strip();
    }
}
