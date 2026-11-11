package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiJobRadarGateway implements JobRadarAiGateway {
    private final ChatClient extractClient;
    private final ChatClient explainClient;
    private final ChatClient debriefClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public SpringAiJobRadarGateway(
            ChatClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("classpath:prompt/job-extract-skills-system-prompt.st") Resource extractPrompt,
            @Value("classpath:prompt/job-evaluate-explain-system-prompt.st") Resource explainPrompt,
            @Value("classpath:prompt/interview-debrief-parse-system-prompt.st") Resource debriefPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String model) {
        this.extractClient = builder.clone().defaultSystem(extractPrompt).build();
        this.explainClient = builder.clone().defaultSystem(explainPrompt).build();
        this.debriefClient = builder.clone().defaultSystem(debriefPrompt).build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public JdSkillExtraction extractJdSkills(String jdText) {
        JsonNode root = call(extractClient, "岗位 JD：\n" + jdText);
        List<JdSkillExtraction.ExtractedSkill> required = skills(root.path("required"));
        List<JdSkillExtraction.ExtractedSkill> nice = skills(root.path("niceToHave"));
        Integer years = nullableInt(root.get("years"));
        String city = textOrNull(root.get("city"));
        return new JdSkillExtraction(required, nice, years, city, root.path("remoteFriendly").asBoolean(false));
    }

    @Override
    public EvaluationExplanation explainEvaluation(String role, String company, JobRadarDomain.MatchResult result) {
        String message = "岗位：" + value(role) + "\n公司：" + value(company)
                + "\n评分：" + result.score()
                + "\n细分：" + result.breakdown()
                + "\n命中与缺口：" + result.hits() + "\n" + result.gaps();
        JsonNode root = call(explainClient, message);
        List<String> actions = new ArrayList<>();
        root.path("topActions").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) actions.add(node.asText().strip());
        });
        return new EvaluationExplanation(textOrNull(root.get("reason")), actions.stream().limit(3).toList());
    }

    @Override
    public DebriefParseResult parseDebrief(String rawText, String roundName, String outcome) {
        JsonNode root = call(debriefClient,
                "轮次：" + value(roundName) + "\n结果：" + value(outcome) + "\n面试回忆：\n" + rawText);
        List<DebriefParseResult.DebriefQuestion> questions = new ArrayList<>();
        root.path("questions").forEach(node -> {
            String text = textOrNull(node.get("text"));
            if (text != null && !text.isBlank()) {
                questions.add(new DebriefParseResult.DebriefQuestion(text.strip(),
                        textOrNull(node.get("category")), node.path("hard").asBoolean(false),
                        textOrNull(node.get("note"))));
            }
        });
        List<String> focus = new ArrayList<>();
        root.path("interviewerFocus").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) focus.add(node.asText().strip());
        });
        return new DebriefParseResult(questions.stream().limit(10).toList(), focus.stream().limit(8).toList(),
                textOrNull(root.get("summary")));
    }

    @Override
    public String model() {
        return model;
    }

    private JsonNode call(ChatClient client, String message) {
        String raw = client.prompt().user(message).call().content();
        if (raw == null || raw.isBlank()) throw new IllegalStateException("模型返回为空");
        String json = raw.strip();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).strip();
            }
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException("模型没有返回合法 JSON", exception);
        }
    }

    private List<JdSkillExtraction.ExtractedSkill> skills(JsonNode nodes) {
        List<JdSkillExtraction.ExtractedSkill> values = new ArrayList<>();
        if (nodes != null && nodes.isArray()) {
            nodes.forEach(node -> {
                String name = textOrNull(node.get("name"));
                if (name != null) {
                    int level = node.path("level").asInt(3);
                    BigDecimal weight = node.has("weight") && node.get("weight").isNumber()
                            ? node.get("weight").decimalValue() : BigDecimal.ONE;
                    values.add(new JdSkillExtraction.ExtractedSkill(name.strip(), level, weight));
                }
            });
        }
        return values.stream().limit(40).toList();
    }

    private static Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() || !node.canConvertToInt() ? null : node.asInt();
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || !node.isTextual() ? null : node.asText();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}
