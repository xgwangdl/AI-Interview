package com.guangge.Interview.resume.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors the resume-builder skill's resume-data.schema.json.
 * Optional values stay nullable; lists are required by the schema and must not be null.
 */
public record ResumeData(
        String language,
        @JsonProperty("document_type") String documentType,
        @JsonProperty("generated_date") String generatedDate,
        @JsonProperty("target_role") String targetRole,
        Person person,
        String headline,
        List<String> summary,
        List<SkillGroup> skills,
        List<Experience> experience,
        List<Education> education,
        List<Project> projects,
        List<PortfolioItem> portfolio,
        List<Certification> certifications,
        List<LanguageSkill> languages,
        List<String> notes) {

    public record Person(
            String name,
            String reading,
            String location,
            String phone,
            String email,
            List<Link> links) { }

    public record Link(String label, String url) { }

    public record SkillGroup(String label, List<String> items) { }

    public record Experience(
            String start,
            String end,
            String company,
            String role,
            List<String> bullets) { }

    public record Education(String start, String end, String institution, String field) { }

    public record Project(
            String start,
            String end,
            String name,
            String role,
            List<String> bullets) { }

    public record PortfolioItem(String name, String description, String url) { }

    public record Certification(String name, String issuer, String date) { }

    public record LanguageSkill(String language, String level) { }
}
