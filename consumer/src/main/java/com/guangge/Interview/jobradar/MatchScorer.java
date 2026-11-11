package com.guangge.Interview.jobradar;

import com.guangge.Interview.jobradar.JobRadarDomain.MatchHit;
import com.guangge.Interview.jobradar.JobRadarDomain.MatchInput;
import com.guangge.Interview.jobradar.JobRadarDomain.MatchResult;
import com.guangge.Interview.jobradar.JobRadarDomain.ScoreBreakdown;
import com.guangge.Interview.jobradar.JobRadarDomain.Skill;
import com.guangge.Interview.jobradar.JobRadarDomain.SkillProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MatchScorer {
    private static final BigDecimal NICE_TO_HAVE_FACTOR = new BigDecimal("0.35");
    private static final BigDecimal NEUTRAL = new BigDecimal("0.8");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final Set<String> SHANGHAI_METRO = Set.of("上海", "苏州", "杭州", "宁波", "南京");
    private static final Set<String> PEARL_RIVER_METRO = Set.of("深圳", "广州", "东莞", "佛山", "珠海");

    private final BigDecimal skillWeight;
    private final BigDecimal seniorityWeight;
    private final BigDecimal salaryWeight;
    private final BigDecimal locationWeight;
    private final BigDecimal freshnessWeight;

    public MatchScorer(
            @Value("${practice.job-radar.weight.skill:0.55}") BigDecimal skillWeight,
            @Value("${practice.job-radar.weight.seniority:0.15}") BigDecimal seniorityWeight,
            @Value("${practice.job-radar.weight.salary:0.15}") BigDecimal salaryWeight,
            @Value("${practice.job-radar.weight.location:0.10}") BigDecimal locationWeight,
            @Value("${practice.job-radar.weight.freshness:0.05}") BigDecimal freshnessWeight) {
        this.skillWeight = skillWeight;
        this.seniorityWeight = seniorityWeight;
        this.salaryWeight = salaryWeight;
        this.locationWeight = locationWeight;
        this.freshnessWeight = freshnessWeight;
        BigDecimal total = skillWeight.add(seniorityWeight).add(salaryWeight).add(locationWeight).add(freshnessWeight);
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Job Radar score weights must add up to 1.0");
        }
    }

    public MatchResult score(MatchInput input) {
        SkillProfile user = input.user() == null
                ? new SkillProfile(List.of(), null, null, null, null) : input.user();
        Map<String, Skill> userSkills = index(user.skills());

        List<WeightedRequirement> requirements = new ArrayList<>();
        addRequirements(requirements, input.required(), true, ONE);
        addRequirements(requirements, input.niceToHave(), false, NICE_TO_HAVE_FACTOR);
        requirements.sort(Comparator.comparing(item -> canonical(item.skill().name())));

        BigDecimal weightedMatches = ZERO;
        BigDecimal totalWeight = ZERO;
        List<MatchHit> hits = new ArrayList<>();
        List<MatchHit> gaps = new ArrayList<>();
        for (WeightedRequirement requirement : requirements) {
            Skill required = requirement.skill();
            Skill actual = userSkills.get(canonical(required.name()));
            int userLevel = actual == null ? 0 : Math.max(0, Math.min(5, actual.level()));
            BigDecimal levelMatch = levelMatch(userLevel, required.level());
            BigDecimal effectiveWeight = positive(required.weight()).multiply(requirement.factor());
            totalWeight = totalWeight.add(effectiveWeight);
            weightedMatches = weightedMatches.add(effectiveWeight.multiply(levelMatch));
            MatchHit hit = new MatchHit(required.name(), userLevel, required.level(),
                    requirement.required(), effectiveWeight, levelMatch);
            hits.add(hit);
            if (levelMatch.compareTo(ONE) < 0) gaps.add(hit);
        }
        gaps.sort(Comparator.comparing(MatchHit::weight).reversed().thenComparing(MatchHit::name));

        BigDecimal skillHit = totalWeight.signum() == 0 ? ZERO
                : weightedMatches.divide(totalWeight, 6, RoundingMode.HALF_UP);
        BigDecimal seniorityFit = seniorityFit(input.jdYears(), user.years());
        BigDecimal salaryFit = salaryFit(input.salaryMinK(), input.salaryMaxK(),
                firstNonNull(input.expectedMinK(), user.expectedMinK()),
                firstNonNull(input.expectedMaxK(), user.expectedMaxK()));
        BigDecimal locationFit = locationFit(input.jdCity(), firstNonBlank(input.userCity(), user.city()),
                input.remoteFriendly());
        BigDecimal freshness = ONE;

        BigDecimal total = skillHit.multiply(skillWeight)
                .add(seniorityFit.multiply(seniorityWeight))
                .add(salaryFit.multiply(salaryWeight))
                .add(locationFit.multiply(locationWeight))
                .add(freshness.multiply(freshnessWeight));
        int score = total.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();
        String confidence = normalizedRequirements(input.required()).size() < 3 ? "LOW" : "HIGH";
        return new MatchResult(score, confidence, recommendation(score),
                new ScoreBreakdown(scale(skillHit), scale(seniorityFit), scale(salaryFit),
                        scale(locationFit), scale(freshness)),
                List.copyOf(hits), List.copyOf(gaps));
    }

    private static List<Skill> normalizedRequirements(List<Skill> values) {
        if (values == null) return List.of();
        Map<String, Skill> unique = new LinkedHashMap<>();
        values.stream().filter(skill -> skill != null && skill.name() != null && !skill.name().isBlank())
                .sorted(Comparator.comparing(skill -> canonical(skill.name())))
                .forEach(skill -> unique.merge(canonical(skill.name()), skill,
                        MatchScorer::higherLevel));
        return List.copyOf(unique.values());
    }

    private static void addRequirements(List<WeightedRequirement> target, List<Skill> source,
                                        boolean required, BigDecimal factor) {
        normalizedRequirements(source).forEach(skill -> target.add(new WeightedRequirement(skill, required, factor)));
    }

    private static Map<String, Skill> index(List<Skill> skills) {
        Map<String, Skill> indexed = new LinkedHashMap<>();
        if (skills != null) {
            skills.stream().filter(skill -> skill != null && skill.name() != null && !skill.name().isBlank())
                    .sorted(Comparator.comparing(skill -> canonical(skill.name())))
                    .forEach(skill -> indexed.merge(canonical(skill.name()), skill, MatchScorer::higherLevel));
        }
        return indexed;
    }

    private static Skill higherLevel(Skill left, Skill right) {
        if (right.level() != left.level()) return right.level() > left.level() ? right : left;
        return right.weight().compareTo(left.weight()) > 0 ? right : left;
    }

    private static BigDecimal levelMatch(int userLevel, int requiredLevel) {
        if (userLevel >= requiredLevel) return ONE;
        if (userLevel == 0) return ZERO;
        if (userLevel == requiredLevel - 1) return new BigDecimal("0.6");
        return ZERO;
    }

    private static BigDecimal seniorityFit(Integer jdYears, Integer userYears) {
        if (jdYears == null || userYears == null) return NEUTRAL;
        BigDecimal gap = BigDecimal.valueOf(Math.max(0, jdYears - userYears));
        BigDecimal fit = ONE.subtract(gap.divide(new BigDecimal("3"), 6, RoundingMode.HALF_UP));
        return clamp(fit);
    }

    private static BigDecimal salaryFit(Integer jobMin, Integer jobMax, Integer expectedMin, Integer expectedMax) {
        if (jobMin == null || jobMax == null || expectedMin == null || expectedMax == null) return NEUTRAL;
        int low = Math.min(jobMin, jobMax);
        int high = Math.max(jobMin, jobMax);
        int expectedLow = Math.min(expectedMin, expectedMax);
        int expectedHigh = Math.max(expectedMin, expectedMax);
        if (expectedLow == expectedHigh) {
            return expectedLow >= low && expectedLow <= high ? ONE : ZERO;
        }
        int overlap = Math.max(0, Math.min(high, expectedHigh) - Math.max(low, expectedLow));
        if (overlap == 0) return ZERO;
        return BigDecimal.valueOf(overlap)
                .divide(BigDecimal.valueOf(expectedHigh - expectedLow), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal locationFit(String jobCity, String userCity, boolean remoteFriendly) {
        String job = normalizeCity(jobCity);
        String user = normalizeCity(userCity);
        if (!job.isBlank() && job.equals(user)) return ONE;
        if (sameMetro(job, user)) return new BigDecimal("0.7");
        if (remoteFriendly || job.contains("远程")) return new BigDecimal("0.9");
        return new BigDecimal("0.2");
    }

    private static boolean sameMetro(String left, String right) {
        if (left.isBlank() || right.isBlank()) return false;
        return (SHANGHAI_METRO.contains(left) && SHANGHAI_METRO.contains(right))
                || (PEARL_RIVER_METRO.contains(left) && PEARL_RIVER_METRO.contains(right));
    }

    public static String recommendation(int score) {
        if (score >= 80) return "strong_apply";
        if (score >= 65) return "apply";
        if (score >= 50) return "prepare_first";
        return "skip";
    }

    static String canonical(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String normalizeCity(String city) {
        if (city == null) return "";
        return city.trim().replace("市", "").replaceAll("\\s+", "");
    }

    private static BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? ONE : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(ZERO) < 0) return ZERO;
        return value.compareTo(ONE) > 0 ? ONE : value;
    }

    private static <T> T firstNonNull(T left, T right) {
        return left != null ? left : right;
    }

    private static String firstNonBlank(String left, String right) {
        return left != null && !left.isBlank() ? left : right;
    }

    private record WeightedRequirement(Skill skill, boolean required, BigDecimal factor) { }
}
