package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeDataValidatorTest {

    private final ResumeDataValidator validator =
            new ResumeDataValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void acceptsResumeDataThatMatchesTheSkillSchema() {
        ResumeData data = validator.parse(validJson(), groundedSource());

        assertEquals("zh", data.language());
        assertEquals("resume", data.documentType());
        assertEquals("Java 后端工程师", data.headline());
        assertEquals(1, data.experience().size());
        assertEquals("800ms 降至 120ms", data.experience().get(0).bullets().get(0));
    }

    @Test
    void rejectsUnknownPropertiesInsteadOfSilentlyDroppingThem() {
        String json = validJson().replace(
                "\"headline\": \"Java 后端工程师\",",
                "\"headline\": \"Java 后端工程师\", \"objective\": \"想做架构师\",");

        assertThrows(ResumeFormatException.class, () -> validator.parse(json, groundedSource()));
    }

    @Test
    void rejectsUnsupportedLanguageAndDocumentType() {
        assertThrows(ResumeFormatException.class,
                () -> validator.parse(validJson().replace("\"language\": \"zh\"", "\"language\": \"fr\""),
                        groundedSource()));
        assertThrows(ResumeFormatException.class,
                () -> validator.parse(validJson().replace("\"document_type\": \"resume\"",
                        "\"document_type\": \"履歴書\""), groundedSource()));
    }

    @Test
    void rejectsMissingNameOrHeadline() {
        assertThrows(ResumeFormatException.class,
                () -> validator.parse(validJson().replace("\"name\": \"张伟\"", "\"name\": \"  \""),
                        groundedSource()));
        assertThrows(ResumeFormatException.class,
                () -> validator.parse(validJson().replace("\"headline\": \"Java 后端工程师\"",
                        "\"headline\": \"\""), groundedSource()));
    }

    @Test
    void rejectsInvalidEmailAndPortfolioUrl() {
        assertThrows(ResumeFormatException.class,
                () -> validator.parse(validJson().replace("zhangwei@example.com", "not-an-email"),
                        groundedSource()));
        assertThrows(ResumeFormatException.class,
                () -> validator.parse(validJson().replace("https://github.com/zhangwei", "github.com/zhangwei"),
                        groundedSource()));
    }

    @Test
    void rejectsMetricsThatAreAbsentFromTheSourceResume() {
        String json = validJson().replace("800ms 降至 120ms", "800ms 降至 120ms，吞吐提升 40%");

        ResumeFormatException exception = assertThrows(ResumeFormatException.class,
                () -> validator.parse(json, groundedSource()));

        assertTrue(exception.getMessage().contains("40%"), exception.getMessage());
    }

    @Test
    void acceptsNumbersAlreadyPresentInTheSourceResume() {
        ResumeData data = validator.parse(validJson(), groundedSource());

        assertEquals("800ms 降至 120ms", data.experience().get(0).bullets().get(0));
    }

    @Test
    void preservesMissingFactsInNotesInsteadOfInventingThem() {
        ResumeData data = validator.parse(validJson(), groundedSource());

        assertEquals("未提供出生日期和住址", data.notes().get(0));
        assertEquals(null, data.person().location());
    }

    private static String groundedSource() {
        return """
                张伟
                邮箱：zhangwei@example.com
                GitHub：https://github.com/zhangwei
                3 年以上 Java 后端开发经验。
                工作经历
                2020.07 - 至今  某某科技  Java 后端工程师
                负责订单系统重构，接口耗时从 800ms 降至 120ms。
                """;
    }

    static String validJson() {
        return """
                {
                  "language": "zh",
                  "document_type": "resume",
                  "generated_date": "2026-11-20",
                  "target_role": "Java 后端工程师",
                  "person": {
                    "name": "张伟",
                    "reading": null,
                    "location": null,
                    "phone": null,
                    "email": "zhangwei@example.com",
                    "links": [
                      {"label": "GitHub", "url": "https://github.com/zhangwei"}
                    ]
                  },
                  "headline": "Java 后端工程师",
                  "summary": ["3 年以上 Java 后端开发经验，关注订单系统与性能优化。"],
                  "skills": [
                    {"label": "后端", "items": ["Java", "Spring Boot", "MySQL"]}
                  ],
                  "experience": [
                    {
                      "start": "2020.07",
                      "end": "至今",
                      "company": "某某科技",
                      "role": "Java 后端工程师",
                      "bullets": ["800ms 降至 120ms"]
                    }
                  ],
                  "education": [],
                  "projects": [],
                  "portfolio": [
                    {"name": "个人项目", "description": null, "url": "https://github.com/zhangwei"}
                  ],
                  "certifications": [],
                  "languages": [],
                  "notes": ["未提供出生日期和住址"]
                }
                """;
    }
}
