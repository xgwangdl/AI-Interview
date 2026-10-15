package com.guangge.Interview.practice.resume;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 归一化规则。
 *
 * 样例取自用户上传的真实 PDF（`徐海生.pdf`）经 Tika 抽出的文本，一字未改：
 * 行尾被排版折断、点号后面跟着零宽空格、邮件超链接单独占了一行。
 */
class ResumeTextNormalizerTest {

    private static final String EXTRACTED = """
            徐海生\u200b
            求职目标：iOS工程师\s

            基本信息\s
            ●\u200b 年龄：24岁\s
            ●\u200b 地址：上海市浦东新区\s
            ●\u200b 手机：10000000000\s
            ●\u200b 邮箱：gbhj@email.com\s

            项目经验\s
            简易微博\s
            ●\u200b 时间：2016.9 - 2016.10\s
            ●\u200b 描述：为上海某公司委托开发一款类似58同城APP，主要运用于iOS和Android平
            台。\s
            ●\u200b 功能：涉及跳蚤市场、在线促销、团购、会员积分、在线求职、同城活动等十二
            大模块。\s
            ●\u200b 职责：主要负责产品设计、产品定位。\s

            教育背景\s
            上海科技大学\s
            ●\u200b 专业：计算机科学与技术\s
            ●\u200b 主修课程：C程序设计、面向对象程序设计(Java)、数据库系统原理、操作系统与
            实验、软件工程导论、算法导论、C++程序设计、Web程序设计(JSP)、C#程序
            https://mailto:gbhj@email.com/
            设计、软件过程与管理、软件体系结构、软件测试、Web数据挖掘、数据结构与
            算法、UML与面向对象分析与设计、大学英语、离散数学。\s

            掌握技能\s
            ●\u200b 开发工具：Xcode、iPhone SDK\s
            ●\u200b 英语：熟练\s
            """;

    @Test
    @DisplayName("一行一条：短文不会被压成一坨，项目符号统一成 -")
    void keepsLineStructure() {
        String text = ResumeTextNormalizer.normalize(EXTRACTED);
        List<String> lines = List.of(text.split("\n"));

        assertThat(lines).contains("徐海生", "求职目标：iOS工程师", "基本信息", "教育背景", "掌握技能");
        assertThat(lines).contains("- 年龄：24岁", "- 地址：上海市浦东新区", "- 英语：熟练", "- 邮箱：gbhj@email.com");
        assertThat(text).doesNotContain("●").doesNotContain("\u200b");
    }

    @Test
    @DisplayName("被排版折断的正文接回上一行")
    void joinsWrappedLines() {
        String text = ResumeTextNormalizer.normalize(EXTRACTED);

        assertThat(text).contains("主要运用于iOS和Android平台。");
        assertThat(text).contains("同城活动等十二大模块。");
        assertThat(text)
                .contains("- 主修课程：C程序设计、面向对象程序设计(Java)、数据库系统原理、操作系统与实验、"
                        + "软件工程导论、算法导论、C++程序设计、Web程序设计(JSP)、C#程序设计、软件过程与管理、"
                        + "软件体系结构、软件测试、Web数据挖掘、数据结构与算法、UML与面向对象分析与设计、"
                        + "大学英语、离散数学。");
    }

    @Test
    @DisplayName("小节名与项目名不会被接到上一行")
    void keepsHeadingsOnTheirOwnLine() {
        List<String> lines = List.of(ResumeTextNormalizer.normalize(EXTRACTED).split("\n"));

        assertThat(lines).contains("教育背景", "上海科技大学", "项目经验", "简易微博");
        assertThat(lines).noneSatisfy(line -> assertThat(line).startsWith("教育背景上"));
    }

    @Test
    @DisplayName("邮件超链接那行噪声丢掉，句子因此接得回来")
    void dropsLinkArtifacts() {
        String text = ResumeTextNormalizer.normalize(EXTRACTED);

        assertThat(text).doesNotContain("mailto");
        assertThat(text).contains("C#程序设计、软件过程与管理");
    }

    @Test
    @DisplayName("空内容不炸")
    void toleratesBlank() {
        assertThat(ResumeTextNormalizer.normalize(null)).isEmpty();
        assertThat(ResumeTextNormalizer.normalize("   \n\n  ")).isEmpty();
    }
}
