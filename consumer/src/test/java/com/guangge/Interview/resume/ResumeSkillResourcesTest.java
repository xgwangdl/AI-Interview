package com.guangge.Interview.resume;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeSkillResourcesTest {

    @Test
    void applicationPackagesTheSharedResumeBuilderResources() throws Exception {
        String schema = read("resume/resume-data.schema.json");
        String systemPrompt = read("resume/spring-ai-system-prompt.md");
        String integrity = read("resume/content-integrity.md");
        String locales = read("resume/locale-guide.md");
        String design = read("resume/design-system.md");

        assertThat(schema).contains("\"title\": \"ResumeData\"").contains("\"document_type\"");
        assertThat(systemPrompt).contains("Produce only JSON").contains("Never infer");
        assertThat(integrity).contains("Evidence hierarchy").contains("Confidentiality");
        assertThat(locales).contains("Chinese").contains("Japanese").contains("JLPT N2");
        assertThat(design).contains("#17324D").contains("A4 portrait");
    }

    private static String read(String path) throws Exception {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
