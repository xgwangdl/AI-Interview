package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.jobradar.JobRadarDomain.Skill;
import com.guangge.Interview.jobradar.JobRadarDomain.SkillProfile;
import com.guangge.Interview.repository.CandidatesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SkillProfileService {
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*年(?:以上)?(?:工作|开发|相关)?经验");
    private static final List<String> CITIES = List.of("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "苏州", "西安", "长沙", "厦门");
    private static final Map<String, String> SKILL_ALIASES = skillAliases();

    private final CandidatesRepository candidates;
    private final ObjectMapper objectMapper;

    public SkillProfileService(CandidatesRepository candidates, ObjectMapper objectMapper) {
        this.candidates = candidates;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SkillProfileView get(String ownerId) {
        return toView(requireCandidate(ownerId));
    }

    @Transactional
    public SkillProfileView update(String ownerId, SkillProfileUpdateRequest request) {
        validate(request);
        Candidates candidate = requireCandidate(ownerId);
        SkillProfile profile = fromRequest(request);
        candidate.setSkillProfileJson(objectMapper.valueToTree(profile));
        return toView(candidates.save(candidate));
    }

    /** Preview extraction only. The user must confirm the preview with PUT before it affects scoring. */
    public SkillProfileView extract(SkillProfileExtractRequest request) {
        String text = request.resumeText();
        LinkedHashMap<String, Skill> found = new LinkedHashMap<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        SKILL_ALIASES.forEach((alias, canonical) -> {
            if (normalized.contains(alias.toLowerCase(Locale.ROOT))) {
                found.merge(MatchScorer.canonical(canonical),
                        new Skill(canonical, 3, BigDecimal.ONE), SkillProfileService::higherLevel);
            }
        });
        Integer years = null;
        Matcher matcher = YEARS.matcher(text);
        if (matcher.find()) years = Integer.parseInt(matcher.group(1));
        String city = CITIES.stream().filter(text::contains).findFirst().orElse(null);
        SkillProfile profile = new SkillProfile(found.values().stream()
                .sorted(Comparator.comparing(skill -> MatchScorer.canonical(skill.name()))).toList(),
                years, city, null, null);
        return view(profile, false);
    }

    @Transactional(readOnly = true)
    public SkillProfile load(String ownerId) {
        Candidates candidate = requireCandidate(ownerId);
        JsonNode node = candidate.getSkillProfileJson();
        if (node == null || node.isNull()) {
            return new SkillProfile(List.of(), null, null, null, null);
        }
        try {
            return objectMapper.treeToValue(node, SkillProfile.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored skill profile", exception);
        }
    }

    @Transactional(readOnly = true)
    public String profileHash(String ownerId) {
        return hash(load(ownerId));
    }

    public String hash(SkillProfile profile) {
        try {
            return JobRadarHashing.sha256(objectMapper.writeValueAsString(normalize(profile)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize skill profile", exception);
        }
    }

    private Candidates requireCandidate(String ownerId) {
        return candidates.findFirstByName(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
    }

    private SkillProfileView toView(Candidates candidate) {
        JsonNode node = candidate.getSkillProfileJson();
        if (node == null || node.isNull()) return view(new SkillProfile(List.of(), null, null, null, null), false);
        try {
            return view(objectMapper.treeToValue(node, SkillProfile.class), true);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored skill profile", exception);
        }
    }

    private SkillProfileView view(SkillProfile profile, boolean configured) {
        SkillProfile normalized = normalize(profile);
        return new SkillProfileView(normalized.skills().stream()
                .map(skill -> new SkillProfileView.SkillItem(skill.name(), skill.level(), skill.weight())).toList(),
                normalized.years(), normalized.city(), normalized.expectedMinK(), normalized.expectedMaxK(),
                configured, hash(normalized));
    }

    private static SkillProfile normalize(SkillProfile profile) {
        LinkedHashMap<String, Skill> unique = new LinkedHashMap<>();
        if (profile != null && profile.skills() != null) {
            profile.skills().stream()
                    .filter(skill -> skill != null && skill.name() != null && !skill.name().isBlank())
                    .sorted(Comparator.comparing(skill -> MatchScorer.canonical(skill.name())))
                    .forEach(skill -> unique.merge(MatchScorer.canonical(skill.name()), skill, SkillProfileService::higherLevel));
        }
        return new SkillProfile(new ArrayList<>(unique.values()), profile == null ? null : profile.years(),
                profile == null ? null : profile.city(), profile == null ? null : profile.expectedMinK(),
                profile == null ? null : profile.expectedMaxK());
    }

    private static Skill higherLevel(Skill left, Skill right) {
        return right.level() > left.level() ? right : left;
    }

    private static SkillProfile fromRequest(SkillProfileUpdateRequest request) {
        return new SkillProfile(request.skills().stream()
                .map(item -> new Skill(item.name().trim(), item.level(), item.weight())).toList(),
                request.years(), trimToNull(request.city()), request.expectedMinK(), request.expectedMaxK());
    }

    private static void validate(SkillProfileUpdateRequest request) {
        Integer min = request.expectedMinK();
        Integer max = request.expectedMaxK();
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "期望薪资下限不能高于上限");
        }
        if (request.skills().stream().map(item -> MatchScorer.canonical(item.name())).distinct().count()
                != request.skills().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能名称不能重复");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> skillAliases() {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        aliases.put("spring boot", "Spring Boot");
        aliases.put("springboot", "Spring Boot");
        aliases.put("spring cloud", "Spring Cloud");
        aliases.put("java", "Java");
        aliases.put("python", "Python");
        aliases.put("typescript", "TypeScript");
        aliases.put("javascript", "JavaScript");
        aliases.put("mysql", "MySQL");
        aliases.put("postgresql", "PostgreSQL");
        aliases.put("redis", "Redis");
        aliases.put("kafka", "Kafka");
        aliases.put("rabbitmq", "RabbitMQ");
        aliases.put("docker", "Docker");
        aliases.put("kubernetes", "Kubernetes");
        aliases.put("k8s", "Kubernetes");
        aliases.put("微服务", "微服务");
        aliases.put("分布式", "分布式系统");
        aliases.put("mybatis", "MyBatis");
        aliases.put("jvm", "JVM");
        aliases.put("linux", "Linux");
        aliases.put("sql", "SQL");
        aliases.put("react", "React");
        aliases.put("vue", "Vue");
        aliases.put("go", "Go");
        aliases.put("golang", "Go");
        return aliases;
    }
}
