package com.guangge.Interview.jobradar;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedSkillExtractor {
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*年(?:以上)?(?:工作|开发|相关)?经验");
    private static final List<String> CITIES = List.of("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "苏州", "西安", "长沙", "厦门");
    private static final Map<String, String> ALIASES = aliases();

    public JdSkillExtraction extract(String text) {
        String source = text == null ? "" : text;
        String normalized = source.toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> found = new LinkedHashMap<>();
        ALIASES.forEach((alias, canonical) -> {
            if (normalized.contains(alias.toLowerCase(Locale.ROOT))) {
                found.putIfAbsent(MatchScorer.canonical(canonical), canonical);
            }
        });
        List<JdSkillExtraction.ExtractedSkill> skills = found.values().stream()
                .sorted(Comparator.comparing(MatchScorer::canonical))
                .map(name -> new JdSkillExtraction.ExtractedSkill(name, 3, BigDecimal.ONE))
                .toList();
        Integer years = null;
        Matcher matcher = YEARS.matcher(source);
        if (matcher.find()) years = Integer.parseInt(matcher.group(1));
        String city = CITIES.stream().filter(source::contains).findFirst().orElse(null);
        return new JdSkillExtraction(skills, List.of(), years, city,
                source.contains("远程") || source.toLowerCase(Locale.ROOT).contains("remote"));
    }

    private static Map<String, String> aliases() {
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
        aliases.put("golang", "Go");
        aliases.put("go语言", "Go");
        return aliases;
    }
}
