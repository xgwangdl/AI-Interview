package com.guangge.Interview.jobradar;

import java.math.BigDecimal;
import java.util.List;

/** Shared value objects for deterministic matching and job-radar DTO mapping. */
public final class JobRadarDomain {
    private JobRadarDomain() { }

    public record Skill(String name, int level, BigDecimal weight) {
        public Skill {
            weight = weight == null ? BigDecimal.ONE : weight;
        }
    }

    public record SkillProfile(
            List<Skill> skills,
            Integer years,
            String city,
            Integer expectedMinK,
            Integer expectedMaxK) { }

    public record MatchInput(
            String jdText,
            List<Skill> required,
            List<Skill> niceToHave,
            SkillProfile user,
            Integer jdYears,
            Integer salaryMinK,
            Integer salaryMaxK,
            Integer expectedMinK,
            Integer expectedMaxK,
            String jdCity,
            String userCity,
            boolean remoteFriendly) { }

    public record MatchHit(
            String name,
            int userLevel,
            int requiredLevel,
            boolean required,
            BigDecimal weight,
            BigDecimal levelMatch) { }

    public record ScoreBreakdown(
            BigDecimal skill,
            BigDecimal seniority,
            BigDecimal salary,
            BigDecimal location,
            BigDecimal freshness) { }

    public record MatchResult(
            int score,
            String confidence,
            String recommendation,
            ScoreBreakdown breakdown,
            List<MatchHit> hits,
            List<MatchHit> gaps) { }
}
