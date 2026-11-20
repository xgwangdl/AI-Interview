package com.guangge.Interview.resume.export;

import com.guangge.Interview.resume.dto.ResumeData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Deterministic plain-text projection used by previews and the PDF compatibility path. */
@Component
public class ResumeTextRenderer {

    public String render(ResumeData data) {
        if (data == null) return "";
        Labels labels = Labels.of(data.language());
        StringBuilder out = new StringBuilder();
        line(out, data.person().name());
        line(out, data.headline());
        String contact = contactLine(data);
        if (!contact.isBlank()) line(out, contact);

        if (hasItems(data.summary())) {
            section(out, labels.summary());
            bullets(out, data.summary());
        }
        if (!data.skills().isEmpty()) {
            section(out, labels.skills());
            for (ResumeData.SkillGroup group : data.skills()) {
                bullet(out, group.label() + "：" + String.join("、", group.items()));
            }
        }
        if (!data.experience().isEmpty()) {
            section(out, labels.experience());
            for (ResumeData.Experience item : data.experience()) {
                line(out, item.company() + "　·　" + item.role());
                line(out, item.start() + " - " + item.end());
                bullets(out, item.bullets());
                out.append('\n');
            }
        }
        if (!data.projects().isEmpty()) {
            section(out, labels.projects());
            for (ResumeData.Project item : data.projects()) {
                line(out, item.name());
                String period = period(item.start(), item.end());
                if (!period.isBlank()) line(out, period);
                line(out, labels.role() + "：" + item.role());
                bullets(out, item.bullets());
                out.append('\n');
            }
        }
        if (!data.education().isEmpty()) {
            section(out, labels.education());
            for (ResumeData.Education item : data.education()) {
                line(out, item.institution() + "　·　" + item.field());
                line(out, item.start() + " - " + item.end());
                out.append('\n');
            }
        }
        if (!data.portfolio().isEmpty()) {
            section(out, labels.portfolio());
            for (ResumeData.PortfolioItem item : data.portfolio()) {
                String description = item.description() == null || item.description().isBlank()
                        ? "" : "：" + item.description();
                bullet(out, item.name() + description + " - " + item.url());
            }
        }
        if (!data.certifications().isEmpty()) {
            section(out, labels.certifications());
            for (ResumeData.Certification item : data.certifications()) {
                List<String> parts = new ArrayList<>();
                parts.add(item.name());
                if (!blank(item.issuer())) parts.add(item.issuer());
                if (!blank(item.date())) parts.add(item.date());
                bullet(out, String.join(" · ", parts));
            }
        }
        if (!data.languages().isEmpty()) {
            section(out, labels.languages());
            for (ResumeData.LanguageSkill item : data.languages()) {
                bullet(out, item.language() + "：" + item.level());
            }
        }
        if (!data.notes().isEmpty()) {
            section(out, labels.notes());
            bullets(out, data.notes());
        }
        return out.toString().strip() + "\n";
    }

    private static String contactLine(ResumeData data) {
        ResumeData.Person person = data.person();
        List<String> values = new ArrayList<>();
        if (!blank(person.phone())) values.add(person.phone());
        if (!blank(person.email())) values.add(person.email());
        if (!blank(person.location())) values.add(person.location());
        for (ResumeData.Link link : person.links()) {
            values.add(link.label() + " " + link.url());
        }
        return String.join("　·　", values);
    }

    private static String period(String start, String end) {
        if (blank(start) && blank(end)) return "";
        if (blank(start)) return end;
        if (blank(end)) return start;
        return start + " - " + end;
    }

    private static boolean hasItems(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static void bullets(StringBuilder out, List<String> values) {
        for (String value : values) {
            if (!blank(value)) bullet(out, value);
        }
    }

    private static void bullet(StringBuilder out, String value) {
        line(out, "- " + value);
    }

    private static void section(StringBuilder out, String title) {
        out.append('\n');
        line(out, title);
    }

    private static void line(StringBuilder out, String value) {
        if (!blank(value)) out.append(value.strip()).append('\n');
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Labels(
            String summary,
            String skills,
            String experience,
            String projects,
            String education,
            String portfolio,
            String certifications,
            String languages,
            String notes,
            String role) {

        static Labels of(String language) {
            return switch (language == null ? "zh" : language) {
                case "en" -> new Labels("PROFILE", "CORE SKILLS", "EXPERIENCE", "SELECTED PROJECTS",
                        "EDUCATION", "PORTFOLIO", "CERTIFICATIONS", "LANGUAGES", "NOTES", "Role");
                case "ja" -> new Labels("職務要約", "活かせる経験・スキル", "職務経歴", "主要プロジェクト",
                        "学歴", "個人開発・ポートフォリオ", "資格・語学", "語学", "補足", "役割");
                default -> new Labels("职业摘要", "核心能力", "工作经历", "项目经历",
                        "教育背景", "作品与链接", "专业资格", "语言能力", "补充说明", "角色");
            };
        }
    }
}
