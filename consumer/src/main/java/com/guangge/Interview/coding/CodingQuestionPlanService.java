package com.guangge.Interview.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.coding.dto.CodingPlanRequest;
import com.guangge.Interview.coding.dto.JudgeOutcome;
import com.guangge.Interview.coding.dto.TestCase;
import com.guangge.Interview.util.JsonText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 出题（含自检闭环）。
 *
 * 为什么必须自检：模型给的测试用例本身可能是错的（期望输出算错、边界用例无解），
 * 那样用户写对了反而被判失败，比不判还糟。所以每道需要执行的题都会拿
 * **模型自己给的参考解**在沙箱里跑一遍全部用例，全过才留下；不过就换一批重试。
 */
@Service
public class CodingQuestionPlanService {

    private static final Logger log = LoggerFactory.getLogger(CodingQuestionPlanService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final CodeJudgeService judge;
    private final CodeRunnerService runner;
    private final int maxAttempts;

    public CodingQuestionPlanService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            CodeJudgeService judge,
            CodeRunnerService runner,
            @Value("classpath:prompt/coding-question-plan-system-prompt.st") Resource systemPrompt,
            @Value("${practice.coding.plan-max-attempts:3}") int maxAttempts) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.objectMapper = objectMapper;
        this.judge = judge;
        this.runner = runner;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** 生成并自检一批题目；全部通过才返回。 */
    public List<CodeQuestionEntity> generate(UUID sessionId, CodingPlanRequest request) {
        List<String> lastFailure = new ArrayList<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return generateOnce(sessionId, request);
            } catch (ResponseStatusException | IllegalStateException exception) {
                lastFailure.add(exception.getMessage());
                log.warn("第 {} 次出题未通过校验：{}", attempt, exception.getMessage());
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI 出题未通过校验（参考解无法通过测试用例），已重试 " + maxAttempts + " 次。最后一次原因："
                        + (lastFailure.isEmpty() ? "未知" : lastFailure.get(lastFailure.size() - 1)));
    }

    private List<CodeQuestionEntity> generateOnce(UUID sessionId, CodingPlanRequest request) {
        String raw = chatClient.prompt().user(userMessage(request)).call().content();
        JsonNode root = parseArray(raw);
        if (root.size() != request.questions().size()) {
            throw new IllegalStateException("题目数量与要求不一致：期望 "
                    + request.questions().size() + "，实际 " + root.size());
        }

        List<CodeQuestionEntity> questions = new ArrayList<>();
        for (int index = 0; index < root.size(); index++) {
            CodingPlanRequest.QuestionSpec spec = request.questions().get(index);
            CodeQuestionEntity entity = toEntity(sessionId, index, spec, root.get(index));
            deriveExpectedOutputs(entity);
            questions.add(entity);
        }
        return questions;
    }

    private CodeQuestionEntity toEntity(UUID sessionId, int index,
                                        CodingPlanRequest.QuestionSpec spec, JsonNode node) {
        QuestionType type = QuestionType.fromId(spec.type())
                .orElseThrow(() -> new IllegalStateException("未知题型：" + spec.type()));
        String title = text(node, "title");
        String statement = text(node, "statement");
        if (title.isBlank() || statement.isBlank()) {
            throw new IllegalStateException("第 " + (index + 1) + " 题缺少 title/statement");
        }

        CodeQuestionEntity entity = new CodeQuestionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setPosition(index);
        entity.setType(type.id());
        entity.setTitle(title);
        entity.setStatement(statement);
        entity.setDifficulty(node.hasNonNull("difficulty") ? node.get("difficulty").asText() : null);
        entity.setTags(node.hasNonNull("tags") ? node.get("tags").asText() : null);
        entity.setStarterCode(node.hasNonNull("starterCode") ? node.get("starterCode").asText() : null);
        entity.setBlanksJson(json(node.get("blanks")));
        entity.setOptionsJson(json(node.get("options")));
        entity.setTestCasesJson(json(node.get("testCases")));
        entity.setSqlSetup(node.hasNonNull("sqlSetup") ? node.get("sqlSetup").asText() : null);
        // answer 只存服务端：参考解 / 正确选项 / 参考要点都在这里，不下发给前端。
        entity.setAnswerJson(json(node.get("answer")));
        entity.setCreatedAt(Instant.now());

        if (type == QuestionType.CODING) {
            CodeLanguage language = CodeLanguage.fromId(spec.language())
                    .orElseThrow(() -> new IllegalStateException("第 " + (index + 1) + " 题语言无效：" + spec.language()));
            entity.setLanguage(language.id());
        }
        validateShape(type, entity, index);
        return entity;
    }

    private void validateShape(QuestionType type, CodeQuestionEntity entity, int index) {
        String prefix = "第 " + (index + 1) + " 题（" + type.id() + "）";
        switch (type) {
            case CODING -> {
                if (isBlank(entity.getStarterCode())) throw new IllegalStateException(prefix + " 缺少 starterCode");
                if (reference(entity).isBlank()) throw new IllegalStateException(prefix + " 缺少参考解 answer.code");
                if (readNode(entity.getTestCasesJson()).isEmpty()) throw new IllegalStateException(prefix + " 缺少测试用例");
            }
            case SQL -> {
                if (isBlank(entity.getSqlSetup())) throw new IllegalStateException(prefix + " 缺少 sqlSetup");
                if (reference(entity).isBlank()) throw new IllegalStateException(prefix + " 缺少参考查询 answer.code");
                if (readNode(entity.getTestCasesJson()).isEmpty()) throw new IllegalStateException(prefix + " 缺少期望结果");
            }
            case FILL_BLANK -> {
                if (isBlank(entity.getStarterCode())) throw new IllegalStateException(prefix + " 缺少 starterCode");
                if (readNode(entity.getBlanksJson()).isEmpty()) throw new IllegalStateException(prefix + " 缺少 blanks");
            }
            case CHOICE -> {
                if (readNode(entity.getOptionsJson()).size() < 2) throw new IllegalStateException(prefix + " 选项不足");
                if (answerArray(entity).isEmpty()) throw new IllegalStateException(prefix + " 缺少正确选项");
            }
            case SHORT_ANSWER -> {
                if (answerArray(entity).isEmpty()) throw new IllegalStateException(prefix + " 缺少参考要点");
            }
        }
    }

    /**
     * 用参考解跑出期望输出。
     *
     * 一开始我让模型同时给出「参考解」和「手算的期望输出」，结果三次都没通过自检——
     * 让模型把输出逐字符算对本身就很难，失败率极高。改成让它只负责「输入 + 一个能跑的参考解」，
     * 期望输出一律由参考解在沙箱里跑出来，这样用例与参考解天然一致，
     * 自检就只需要回答一个问题：参考解能不能正常跑完。
     */
    private void deriveExpectedOutputs(CodeQuestionEntity entity) {
        QuestionType type = QuestionType.fromId(entity.getType()).orElseThrow();
        if (type == QuestionType.CODING) {
            CodeLanguage language = CodeLanguage.fromId(entity.getLanguage())
                    .orElseThrow(() -> new IllegalStateException("语言无效：" + entity.getLanguage()));
            List<TestCase> cases = readCases(entity.getTestCasesJson());
            List<TestCase> derived = new ArrayList<>();
            boolean hasOutput = false;
            for (TestCase testCase : cases) {
                CodeRunResult run = runner.run(language, reference(entity), testCase.stdin());
                if (!run.ok()) {
                    throw new IllegalStateException("参考解跑不通（用例 " + testCase.name() + "）：" + failureBrief(run));
                }
                String output = CodeJudgeService.normalizeOutput(run.stdout());
                if (!output.isBlank()) hasOutput = true;
                derived.add(new TestCase(testCase.name(), testCase.stdin(), output, testCase.hidden()));
            }
            if (!hasOutput) {
                throw new IllegalStateException("参考解对所有用例都没有输出，题目没有意义");
            }
            entity.setTestCasesJson(json(objectMapper.valueToTree(derived)));
        } else if (type == QuestionType.SQL) {
            List<TestCase> cases = readCases(entity.getTestCasesJson());
            TestCase first = cases.isEmpty() ? new TestCase("结果集", "", "", false) : cases.get(0);
            CodeRunResult run = runner.run(CodeLanguage.PYTHON,
                    judge.sqlScript(entity.getSqlSetup(), reference(entity)), "");
            if (!run.ok()) {
                throw new IllegalStateException("参考查询跑不通：" + failureBrief(run));
            }
            String output = CodeJudgeService.normalizeOutput(run.stdout());
            if (output.isBlank()) {
                throw new IllegalStateException("参考查询没有返回任何结果行");
            }
            entity.setTestCasesJson(json(objectMapper.valueToTree(List.of(new TestCase(first.name(), "（题目自带数据）", output, false)))));
        }
    }

    private List<TestCase> readCases(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TestCase.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String failureBrief(CodeRunResult run) {
        String detail = run.compileOutput() == null ? "" : run.compileOutput().strip();
        if (detail.isBlank() && run.stderr() != null) detail = run.stderr().strip();
        if (detail.isBlank()) detail = "退出码 " + run.exitCode();
        return detail.length() > 300 ? detail.substring(0, 300) + "…" : detail;
    }

    private String reference(CodeQuestionEntity entity) {
        try {
            JsonNode answer = objectMapper.readTree(entity.getAnswerJson());
            return answer != null && answer.hasNonNull("code") ? answer.get("code").asText() : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private List<JsonNode> answerArray(CodeQuestionEntity entity) {
        try {
            JsonNode answer = objectMapper.readTree(entity.getAnswerJson());
            if (answer == null) return List.of();
            for (String field : List.of("keys", "points")) {
                if (answer.hasNonNull(field) && answer.get(field).isArray() && answer.get(field).size() > 0) {
                    return toList(answer.get(field));
                }
            }
            return List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<JsonNode> readNode(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isArray() ? toList(node) : List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private JsonNode parseArray(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalStateException("模型返回为空");
        try {
            JsonNode node = objectMapper.readTree(JsonText.stripCodeFence(raw));
            if (node == null || !node.isArray() || node.isEmpty()) {
                throw new IllegalStateException("模型返回的不是非空 JSON 数组");
            }
            return node;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("模型返回的 JSON 无法解析：" + exception.getMessage());
        }
    }

    private String json(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("题目数据序列化失败", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String userMessage(CodingPlanRequest request) {
        StringBuilder specs = new StringBuilder();
        for (int index = 0; index < request.questions().size(); index++) {
            CodingPlanRequest.QuestionSpec spec = request.questions().get(index);
            specs.append(index + 1).append(". ").append(spec.type());
            if (spec.language() != null && !spec.language().isBlank()) {
                specs.append(" / ").append(spec.language());
            }
            specs.append('\n');
        }
        return """
                岗位：%s
                公司：%s
                岗位描述：%s
                难度：%s
                请按顺序出以下 %d 道题，输出 JSON 数组：
                %s
                """.formatted(
                request.role(),
                request.company() == null || request.company().isBlank() ? "（未提供）" : request.company(),
                request.jobDescription() == null || request.jobDescription().isBlank() ? "（未提供）" : request.jobDescription(),
                request.difficulty() == null || request.difficulty().isBlank() ? "standard" : request.difficulty(),
                request.questions().size(),
                specs);
    }
}
