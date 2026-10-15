package com.guangge.Interview.practice.resume;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 简历正文归一化。**关键是保住行结构。**
 *
 * 上一版把整篇文本里所有空白（含换行）压成一个空格，结果一份排版清楚的 PDF 被读成
 * 一整段「徐海生 求职目标：Android开发工程师 基本信息 ● 年龄：24岁 ● 地址：…」糊在一起的文字。
 * 之后 AI 优化只能在这坨文字上继续，导出的 PDF 自然是一大段 —— 用户的原话是「优化以后就是个垃圾」。
 *
 * 现在的规则：
 * <ul>
 *   <li>按行处理，行内空白压缩、零宽字符丢掉；</li>
 *   <li>PDF 里被排版折断的行接回上一行（「…iOS和Android平」+「台。」）；</li>
 *   <li>项目符号统一成 <code>- </code>（● / • / ▪ / ◆ / ■ / ※ 都认）；</li>
 *   <li>PDF 邮件超链接被抽成单独一行的那种噪声丢掉；</li>
 *   <li>其余换行原样保留。</li>
 * </ul>
 */
public final class ResumeTextNormalizer {

    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");
    private static final Pattern INLINE_SPACE = Pattern.compile("[\\s\\u00A0\\u3000]+");
    /** 零宽空格（PDF 的点号后面常常跟一个）、零宽连接符、BOM、软连字符。 */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]");
    /** Tika 会把邮件超链接注解抽成单独一行（如 https://mailto:xx@yy.com/），那是噪声不是内容。 */
    private static final Pattern LINK_ARTIFACT = Pattern.compile("(?i)^(https?://)?mailto:[^\\s]+$");

    private static final String GLYPH_BULLETS = "●•▪◆■※‣∙";
    /**
     * 折行合并的下限：上一行至少这么长，才可能是一句被排版折断的正文。
     * 短行（小节名、项目名、"上海科技大学"）后面跟的是新内容，不能接上去。
     */
    private static final int WRAP_MIN_LENGTH = 20;

    private static final Set<String> SECTION_TITLES = Set.of(
            "基本信息", "个人信息", "基本资料", "联系方式", "求职意向", "求职目标",
            "教育背景", "教育经历", "工作经历", "工作经验", "实习经历", "校园经历", "实践经历",
            "项目经验", "项目经历", "项目背景", "专业技能", "掌握技能", "技能清单", "技能特长",
            "获奖情况", "荣誉奖项", "证书", "培训经历", "自我评价", "个人评价", "其他");

    private ResumeTextNormalizer() { }

    /** 把抽取出来的原始文本整理成"一行一条内容"的正文。 */
    public static String normalize(String extracted) {
        if (extracted == null || extracted.isBlank()) return "";
        List<String> lines = new ArrayList<>();
        for (String raw : LINE_BREAK.split(extracted)) {
            String line = cleanLine(raw);
            if (line.isEmpty() || LINK_ARTIFACT.matcher(line).matches()) continue;
            if (isGlyphBullet(line)) line = "- " + line.substring(1).strip();
            if (shouldJoinWithPrevious(lines, line)) {
                lines.set(lines.size() - 1, join(lines.get(lines.size() - 1), line));
            } else {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private static String cleanLine(String raw) {
        String withoutInvisible = INVISIBLE.matcher(raw).replaceAll("");
        return INLINE_SPACE.matcher(withoutInvisible).replaceAll(" ").strip();
    }

    private static boolean isGlyphBullet(String line) {
        return !line.isEmpty() && GLYPH_BULLETS.indexOf(line.charAt(0)) >= 0;
    }

    /** 已经是条目行（`- xxx` / `* xxx`）或刚被转成条目行。 */
    private static boolean startsItem(String line) {
        if (line.isEmpty()) return false;
        char first = line.charAt(0);
        if (GLYPH_BULLETS.indexOf(first) >= 0) return true;
        return (first == '-' || first == '*') && line.length() > 1 && line.charAt(1) == ' ';
    }

    private static boolean shouldJoinWithPrevious(List<String> lines, String line) {
        if (lines.isEmpty() || startsItem(line)) return false;
        String previous = lines.get(lines.size() - 1);
        if (previous.length() < WRAP_MIN_LENGTH) return false;
        if (endsSentence(previous)) return false;
        return !isSectionTitle(line);
    }

    private static boolean endsSentence(String line) {
        return "。！？!?；;：:".indexOf(line.charAt(line.length() - 1)) >= 0;
    }

    private static boolean isSectionTitle(String line) {
        String value = line.strip();
        if (value.endsWith("：") || value.endsWith(":")) value = value.substring(0, value.length() - 1).strip();
        return value.length() <= 12 && SECTION_TITLES.contains(value);
    }

    /** 中文之间直接相接，拉丁文之间补一个空格（英文换行断在词与词之间）。 */
    private static String join(String previous, String line) {
        boolean cjk = isCjk(previous.charAt(previous.length() - 1)) || isCjk(line.charAt(0));
        return previous + (cjk ? "" : " ") + line;
    }

    private static boolean isCjk(char value) {
        return (value >= 0x3000 && value <= 0x303F)
                || (value >= 0x3040 && value <= 0x9FFF)
                || (value >= 0xF900 && value <= 0xFAFF)
                || (value >= 0xFF00 && value <= 0xFF60);
    }
}
