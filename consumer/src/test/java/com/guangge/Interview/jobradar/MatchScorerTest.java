package com.guangge.Interview.jobradar;

import com.guangge.Interview.jobradar.JobRadarDomain.MatchInput;
import com.guangge.Interview.jobradar.JobRadarDomain.MatchResult;
import com.guangge.Interview.jobradar.JobRadarDomain.Skill;
import com.guangge.Interview.jobradar.JobRadarDomain.SkillProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScorerTest {
    private final MatchScorer scorer = new MatchScorer(
            new BigDecimal("0.55"), new BigDecimal("0.15"), new BigDecimal("0.15"),
            new BigDecimal("0.10"), new BigDecimal("0.05"));

    @Test
    void f1GoodMatchScoresExactly78() {
        MatchResult result = scorer.score(new MatchInput(
                "Java backend",
                List.of(skill("Java", 3), skill("Spring Boot", 3), skill("MySQL", 2)),
                List.of(skill("Kafka", 1)),
                new SkillProfile(List.of(skill("Java", 4), skill("Spring Boot", 3), skill("MySQL", 0)),
                        3, "杭州", 25, 25),
                3, 20, 30, null, null, "杭州", "杭州", false));

        assertThat(result.score()).as(result.toString()).isEqualTo(78);
        assertThat(result.recommendation()).isEqualTo("apply");
        assertThat(result.confidence()).isEqualTo("HIGH");
    }

    @Test
    void f3SevereMismatchScoresExactly7() {
        MatchResult result = scorer.score(new MatchInput(
                "Senior platform engineer",
                List.of(skill("Java", 4), skill("Kafka", 4), skill("Kubernetes", 4),
                        skill("Go", 3), skill("分布式系统", 4)),
                List.of(),
                new SkillProfile(List.of(skill("Java", 1), skill("Python", 3)),
                        1, "成都", 25, 30),
                5, 10, 14, null, null, "杭州", "成都", false));

        assertThat(result.score()).isEqualTo(7);
        assertThat(result.recommendation()).isEqualTo("skip");
    }

    @Test
    void duplicateAndCaseVariantSkillsDoNotChangeTheScore() {
        MatchResult first = scorer.score(new MatchInput("", List.of(skill("Spring Boot", 3), skill("springboot", 3)),
                List.of(), new SkillProfile(List.of(skill("SPRING BOOT", 3)), 1, "杭州", 20, 20),
                1, 20, 20, null, null, "杭州", "杭州", false));
        MatchResult second = scorer.score(new MatchInput("", List.of(skill("Spring Boot", 3)),
                List.of(), new SkillProfile(List.of(skill("Spring Boot", 3)), 1, "杭州", 20, 20),
                1, 20, 20, null, null, "杭州", "杭州", false));

        assertThat(first.score()).isEqualTo(second.score());
    }

    @Test
    void fewerThanThreeRequiredSkillsIsLowConfidence() {
        MatchResult result = scorer.score(new MatchInput("", List.of(skill("Java", 3), skill("MySQL", 3)),
                List.of(), new SkillProfile(List.of(skill("Java", 3), skill("MySQL", 3)), 1,
                "杭州", 20, 20), 1, 20, 20, null, null, "杭州", "杭州", false));

        assertThat(result.confidence()).isEqualTo("LOW");
    }

    private static Skill skill(String name, int level) {
        return new Skill(name, level, BigDecimal.ONE);
    }
}
