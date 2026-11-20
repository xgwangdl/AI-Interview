package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeData;
import com.guangge.Interview.util.JsonText;
import com.guangge.Interview.util.NumberClaims;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ResumeDataValidator {

    private static final Set<String> LANGUAGES = Set.of("zh", "en", "ja");
    private static final Set<String> DOCUMENT_TYPES = Set.of("resume", "cv", "career_history");
    private static final Pattern GENERATED_DATE = Pattern.compile("^\\d{4}-\\d{2}(-\\d{2})?$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ObjectMapper objectMapper;

    public ResumeDataValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public ResumeData parse(String raw, String groundedSource) {
        if (raw == null || raw.isBlank()) {
            throw new ResumeFormatException("resumeData 为空");
        }
        ResumeData data;
        try {
            data = objectMapper.readValue(JsonText.stripCodeFence(raw), ResumeData.class);
        } catch (Exception exception) {
            throw new ResumeFormatException("resumeData 不符合 skill schema：" + exception.getMessage(), exception);
        }
        return validate(data, groundedSource);
    }

    public ResumeData validate(ResumeData data, String groundedSource) {
        if (data == null) throw new ResumeFormatException("resumeData 缺失");
        if (!LANGUAGES.contains(data.language())) {
            throw new ResumeFormatException("language 不支持：" + data.language());
        }
        if (!DOCUMENT_TYPES.contains(data.documentType())) {
            throw new ResumeFormatException("document_type 不支持：" + data.documentType());
        }
        if (data.generatedDate() != null && !GENERATED_DATE.matcher(data.generatedDate()).matches()) {
            throw new ResumeFormatException("generated_date 必须为 YYYY-MM 或 YYYY-MM-DD");
        }
        if (data.person() == null || blank(data.person().name())) {
            throw new ResumeFormatException("person.name 不能为空");
        }
        if (blank(data.headline())) {
            throw new ResumeFormatException("headline 不能为空");
        }
        if (data.person().email() != null && !EMAIL.matcher(data.person().email()).matches()) {
            throw new ResumeFormatException("person.email 格式不正确");
        }
        validateLinks(data.person().links(), "person.links");
        validatePortfolio(data.portfolio());
        requireList(data.summary(), "summary");
        requireList(data.skills(), "skills");
        requireList(data.experience(), "experience");
        requireList(data.education(), "education");
        requireList(data.projects(), "projects");
        requireList(data.portfolio(), "portfolio");
        requireList(data.certifications(), "certifications");
        requireList(data.languages(), "languages");
        requireList(data.notes(), "notes");
        validateSkills(data.skills());
        validateExperience(data.experience());
        validateEducation(data.education());
        validateProjects(data.projects());
        validateCertifications(data.certifications());
        validateLanguages(data.languages());

        if (groundedSource != null) {
            Set<String> unsupported = NumberClaims.unsupported(groundedText(data), groundedSource);
            if (!unsupported.isEmpty()) {
                throw new ResumeFormatException("结构化简历出现了原文没有的数字："
                        + String.join("、", unsupported) + "。真实数据必须由用户补充");
            }
        }
        return data;
    }

    private static void validateLinks(List<ResumeData.Link> links, String field) {
        requireList(links, field);
        for (int index = 0; index < links.size(); index++) {
            ResumeData.Link link = links.get(index);
            if (link == null || blank(link.label())) {
                throw new ResumeFormatException(field + "[" + index + "].label 不能为空");
            }
            requireHttpUrl(link.url(), field + "[" + index + "].url");
        }
    }

    private static void validatePortfolio(List<ResumeData.PortfolioItem> portfolio) {
        requireList(portfolio, "portfolio");
        for (int index = 0; index < portfolio.size(); index++) {
            ResumeData.PortfolioItem item = portfolio.get(index);
            if (item == null || blank(item.name())) {
                throw new ResumeFormatException("portfolio[" + index + "].name 不能为空");
            }
            requireHttpUrl(item.url(), "portfolio[" + index + "].url");
        }
    }

    private static void validateSkills(List<ResumeData.SkillGroup> skills) {
        for (int index = 0; index < skills.size(); index++) {
            ResumeData.SkillGroup group = skills.get(index);
            if (group == null || blank(group.label())) {
                throw new ResumeFormatException("skills[" + index + "].label 不能为空");
            }
            requireList(group.items(), "skills[" + index + "].items");
            for (int item = 0; item < group.items().size(); item++) {
                if (blank(group.items().get(item))) {
                    throw new ResumeFormatException("skills[" + index + "].items 不能包含空值");
                }
            }
        }
    }

    private static void validateExperience(List<ResumeData.Experience> experience) {
        for (int index = 0; index < experience.size(); index++) {
            ResumeData.Experience item = experience.get(index);
            String field = "experience[" + index + "]";
            if (item == null || blank(item.start()) || blank(item.end())
                    || blank(item.company()) || blank(item.role())) {
                throw new ResumeFormatException(field + " 缺少必填字段");
            }
            requireNonBlankList(item.bullets(), field + ".bullets");
        }
    }

    private static void validateEducation(List<ResumeData.Education> education) {
        for (int index = 0; index < education.size(); index++) {
            ResumeData.Education item = education.get(index);
            String field = "education[" + index + "]";
            if (item == null || blank(item.start()) || blank(item.end())
                    || blank(item.institution()) || blank(item.field())) {
                throw new ResumeFormatException(field + " 缺少必填字段");
            }
        }
    }

    private static void validateProjects(List<ResumeData.Project> projects) {
        for (int index = 0; index < projects.size(); index++) {
            ResumeData.Project item = projects.get(index);
            String field = "projects[" + index + "]";
            if (item == null || blank(item.name()) || blank(item.role())) {
                throw new ResumeFormatException(field + " 缺少必填字段");
            }
            requireNonBlankList(item.bullets(), field + ".bullets");
        }
    }

    private static void validateCertifications(List<ResumeData.Certification> certifications) {
        for (int index = 0; index < certifications.size(); index++) {
            ResumeData.Certification item = certifications.get(index);
            if (item == null || blank(item.name())) {
                throw new ResumeFormatException("certifications[" + index + "].name 不能为空");
            }
        }
    }

    private static void validateLanguages(List<ResumeData.LanguageSkill> languages) {
        for (int index = 0; index < languages.size(); index++) {
            ResumeData.LanguageSkill item = languages.get(index);
            if (item == null || blank(item.language()) || blank(item.level())) {
                throw new ResumeFormatException("languages[" + index + "] 缺少语言或等级");
            }
        }
    }

    private static void requireHttpUrl(String value, String field) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme())
                    || blank(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new ResumeFormatException(field + " 必须是 http/https URL");
        }
    }

    private static String groundedText(ResumeData data) {
        StringBuilder text = new StringBuilder();
        ResumeData.Person person = data.person();
        append(text, person.name(), person.reading(), person.location(), person.phone(), person.email());
        person.links().forEach(link -> append(text, link.label(), link.url()));
        append(text, data.headline());
        data.summary().forEach(value -> append(text, value));
        data.skills().forEach(group -> {
            append(text, group.label());
            group.items().forEach(value -> append(text, value));
        });
        data.experience().forEach(item -> {
            append(text, item.start(), item.end(), item.company(), item.role());
            item.bullets().forEach(value -> append(text, value));
        });
        data.education().forEach(item -> append(
                text, item.start(), item.end(), item.institution(), item.field()));
        data.projects().forEach(item -> {
            append(text, item.start(), item.end(), item.name(), item.role());
            item.bullets().forEach(value -> append(text, value));
        });
        data.portfolio().forEach(item -> append(text, item.name(), item.description(), item.url()));
        data.certifications().forEach(item -> append(text, item.name(), item.issuer(), item.date()));
        data.languages().forEach(item -> append(text, item.language(), item.level()));
        return text.toString();
    }

    private static void append(StringBuilder text, String... values) {
        for (String value : values) {
            if (value != null) text.append(value).append('\n');
        }
    }

    private static void requireList(List<?> value, String field) {
        if (value == null) throw new ResumeFormatException(field + " 不能为 null");
    }

    private static void requireNonBlankList(List<String> value, String field) {
        requireList(value, field);
        for (String item : value) {
            if (blank(item)) throw new ResumeFormatException(field + " 不能包含空值");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
