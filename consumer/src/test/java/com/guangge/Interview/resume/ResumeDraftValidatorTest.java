package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeDraftResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 从零创建的输出校验：结构与"不许编造"这条底线。 */
class ResumeDraftValidatorTest {

    private final ResumeDraftValidator validator = new ResumeDraftValidator(new ObjectMapper());

    @Test
    @DisplayName("正常返回：正文、追问、占位符都留下来")
    void parsesAGoodAnswer() {
        ResumeDraftResult result = validator.parse("""
                {
                  "resumeText": "张三\\n求职目标：Java 后端工程师\\n\\n教育背景\\n- 【请补充：学校、专业、起止时间】",
                  "questions": ["补一条量化结果：接口耗时、数据量级之类"],
                  "placeholders": ["【请补充：学校、专业、起止时间】"]
                }
                """);

        assertThat(result.resumeText()).startsWith("张三");
        assertThat(result.questions()).containsExactly("补一条量化结果：接口耗时、数据量级之类");
        assertThat(result.placeholders()).containsExactly("【请补充：学校、专业、起止时间】");
    }

    @Test
    @DisplayName("模型用代码块包着 JSON 也能救回来")
    void stripsCodeFence() {
        ResumeDraftResult result = validator.parse("""
                ```json
                {"resumeText": "张三\\n求职目标：【请补充：目标岗位】", "questions": [], "placeholders": ["【请补充：目标岗位】"]}
                ```
                """);

        assertThat(result.resumeText()).contains("求职目标");
    }

    @Test
    @DisplayName("占位符以正文为准，模型多报少报都不算数")
    void placeholdersComeFromTheText() {
        ResumeDraftResult result = validator.parse("""
                {
                  "resumeText": "张三\\n- 【请补充：电话】\\n- 【请补充：邮箱】",
                  "questions": [],
                  "placeholders": ["【请补充：电话】", "【请补充：年龄】"]
                }
                """);

        assertThat(result.placeholders()).containsExactly("【请补充：电话】", "【请补充：邮箱】");
    }

    @Test
    @DisplayName("声称留了占位符、正文里一个都没有 → 判为编造，拦下来")
    void rejectsFabricationDisguisedAsPlaceholders() {
        assertThatThrownBy(() -> validator.parse("""
                {"resumeText": "张三\\n工作经历\\n- 时间：2021.03 - 2022.01\\n- 职责：负责订单系统",
                 "questions": [],
                 "placeholders": ["【请补充：订单量级】"]}
                """))
                .isInstanceOf(ResumeFormatException.class)
                .hasMessageContaining("疑似编造");
    }

    @Test
    @DisplayName("正文为空、JSON 不合法、空响应都要报格式错误")
    void rejectsMalformedAnswers() {
        assertThatThrownBy(() -> validator.parse("{}"))
                .isInstanceOf(ResumeFormatException.class)
                .hasMessageContaining("resumeText");
        assertThatThrownBy(() -> validator.parse("我不知道该怎么写"))
                .isInstanceOf(ResumeFormatException.class)
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> validator.parse("  "))
                .isInstanceOf(ResumeFormatException.class);
    }

    @Test
    @DisplayName("把缺失项写成「未提供」这类字面量也要拦下来：它不会被高亮")
    void rejectsLiteralMissingWords() {
        assertThatThrownBy(() -> validator.parse("""
                {"resumeText": "张三\\n基本信息\\n- 电话：未提供\\n- 【请补充：邮箱】",
                 "questions": [], "placeholders": ["【请补充：邮箱】"]}
                """))
                .isInstanceOf(ResumeFormatException.class)
                .hasMessageContaining("未提供")
                .hasMessageContaining("【请补充：…】");
    }

    @Test
    @DisplayName("没信息量的占位符（【请补充：…】）也拦下来：用户看不出要补什么")
    void rejectsUninformativePlaceholders() {
        assertThatThrownBy(() -> validator.parse("""
                {"resumeText": "张三\\n基本信息\\n- 电话：【请补充：…】", "questions": [], "placeholders": []}
                """))
                .isInstanceOf(ResumeFormatException.class)
                .hasMessageContaining("没写清要补什么");

        // 写清了就不拦
        ResumeDraftResult ok = validator.parse("""
                {"resumeText": "张三\\n基本信息\\n- 电话：【请补充：手机号】", "questions": [], "placeholders": []}
                """);
        assertThat(ok.placeholders()).containsExactly("【请补充：手机号】");
    }

    @Test
    @DisplayName("追问最多留 3 条，多的砍掉而不是让整次生成失败")
    void keepsAtMostThreeQuestions() {
        List<String> many = new ArrayList<>(List.of("一", "二", "三", "四", "五"));

        ResumeDraftResult result = validator.validate(new ResumeDraftResult(
                "张三\n- 【请补充：电话】", many, List.of()));

        assertThat(result.questions()).containsExactly("一", "二", "三");
    }

    @Test
    @DisplayName("空素材的骨架也是合法结果：正文有占位符，就说明它没编造")
    void acceptsASkeletonWithoutMaterial() {
        ResumeDraftResult result = validator.parse("""
                {
                  "resumeText": "【请补充：姓名】\\n求职目标：【请补充：目标岗位】\\n\\n教育背景\\n- 【请补充：学校、专业、起止时间】",
                  "questions": ["补上目标岗位能让内容更聚焦"],
                  "placeholders": ["【请补充：姓名】", "【请补充：目标岗位】", "【请补充：学校、专业、起止时间】"]
                }
                """);

        assertThat(result.placeholders()).hasSize(3);
        assertThat(result.questions()).hasSize(1);
    }
}
