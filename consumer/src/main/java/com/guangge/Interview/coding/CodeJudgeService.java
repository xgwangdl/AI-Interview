package com.guangge.Interview.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.coding.dto.Blank;
import com.guangge.Interview.coding.dto.CaseResult;
import com.guangge.Interview.coding.dto.JudgeOutcome;
import com.guangge.Interview.coding.dto.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 五种题型的判分。
 *
 * 原则：能客观判的绝不交给模型 ——
 * <ul>
 *   <li>上机编程：跑测试用例（用户确认的「以运行结果为准」）</li>
 *   <li>SQL：在内存 SQLite 上执行，比对结果集</li>
 *   <li>填空 / 选择：确定性比对</li>
 *   <li>简答：没有唯一答案，才交给模型按参考要点评分</li>
 * </ul>
 *
 * visibleOnly=true 时只跑非隐藏用例（前端「运行」按钮），false 时跑全部（「提交」）。
 */
@Service
public class CodeJudgeService {

    private static final Logger log = LoggerFactory.getLogger(CodeJudgeService.class);
    private static final double SHORT_ANSWER_PASS_SCORE = 60;

    private final CodeRunnerService runner;
    private final ObjectMapper objectMapper;
    private final ShortAnswerGrader shortAnswerGrader;

    public CodeJudgeService(CodeRunnerService runner, ObjectMapper objectMapper, ShortAnswerGrader shortAnswerGrader) {
        this.runner = runner;
        this.objectMapper = objectMapper;
        this.shortAnswerGrader = shortAnswerGrader;
    }

    public JudgeOutcome judge(CodeQuestionEntity question, String answer, boolean visibleOnly) {
        QuestionType type = QuestionType.fromId(question.getType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "未知题型：" + question.getType()));
        String submitted = answer == null ? "" : answer;
        return switch (type) {
            case CODING -> judgeCoding(question, submitted, visibleOnly);
            case SQL -> judgeSql(question, submitted, visibleOnly);
            case FILL_BLANK -> judgeFillBlank(question, submitted);
            case CHOICE -> judgeChoice(question, submitted);
            case SHORT_ANSWER -> judgeShortAnswer(question, submitted);
        };
    }

    // ---------- 上机编程 ----------

    private JudgeOutcome judgeCoding(CodeQuestionEntity question, String source, boolean visibleOnly) {
        if (source.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代码不能为空");
        }
        CodeLanguage language = CodeLanguage.fromId(question.getLanguage())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "未知语言：" + question.getLanguage()));
        List<TestCase> cases = readList(question.getTestCasesJson(), TestCase.class);
        if (cases.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这道题没有配置测试用例");
        }

        List<CaseResult> results = new ArrayList<>();
        long totalRuntime = 0;
        int passed = 0;
        int total = 0;
        String stdout = "";
        String stderr = "";
        String compileOutput = "";

        for (int index = 0; index < cases.size(); index++) {
            TestCase testCase = cases.get(index);
            if (visibleOnly && testCase.hidden()) continue;
            total++;

            CodeRunResult run = runner.run(language, source, testCase.stdin());
            totalRuntime += run.durationMs();
            if (index == 0) {
                stdout = run.stdout();
                stderr = run.stderr();
                compileOutput = run.compileOutput();
            }

            boolean passedCase = run.ok() && normalizeOutput(run.stdout()).equals(normalizeOutput(testCase.expectedStdout()));
            if (passedCase) passed++;

            results.add(new CaseResult(index + 1, testCase.name(), passedCase, testCase.hidden(),
                    run.durationMs(), failureMessage(run, testCase, passedCase),
                    testCase.hidden() ? null : testCase.stdin(),
                    testCase.hidden() ? null : testCase.expectedStdout(),
                    testCase.hidden() ? null : run.stdout()));

            // 编译都不过就没必要再跑剩下的用例，白等 N 次容器启动。
            if (!run.compileOutput().isBlank() && !run.ok()) {
                log.debug("编译失败，跳过后面的用例：{}", run.compileOutput());
                break;
            }
        }

        if (total == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可运行的用例");
        }
        String status = passed == total ? JudgeOutcome.PASSED
                : (!compileOutput.isBlank() ? JudgeOutcome.ERROR : JudgeOutcome.FAILED);
        return JudgeOutcome.of(status, passed, total, totalRuntime, stdout, stderr, compileOutput, results);
    }

    private String failureMessage(CodeRunResult run, TestCase testCase, boolean passedCase) {
        if (passedCase) return null;
        if (run.timedOut()) return "执行超时";
        if (!run.compileOutput().isBlank()) return "编译失败";
        if (!run.ok()) return "运行出错（退出码 " + run.exitCode() + "）";
        return "输出与期望不一致";
    }

    // ---------- SQL ----------

    /**
     * 在内存 SQLite 上执行候选人写的查询，比对结果集。
     *
     * 复用已拉取的 Python 镜像（自带 sqlite3），不需要额外引入数据库镜像，
     * 同时天然拿到与代码题一致的沙箱隔离（禁网、限内存、限时）。
     */
    private JudgeOutcome judgeSql(CodeQuestionEntity question, String query, boolean visibleOnly) {
        if (query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 不能为空");
        }
        List<TestCase> cases = readList(question.getTestCasesJson(), TestCase.class);
        TestCase expected = cases.stream().filter(item -> !visibleOnly || !item.hidden()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "这道题没有配置期望结果"));

        String setup = question.getSqlSetup() == null ? "" : question.getSqlSetup();
        CodeRunResult run = runner.run(CodeLanguage.PYTHON, sqlScript(setup, query), "");
        boolean passedCase = run.ok() && normalizeOutput(run.stdout()).equals(normalizeOutput(expected.expectedStdout()));
        List<CaseResult> results = List.of(new CaseResult(1, expected.name() == null ? "结果集" : expected.name(),
                passedCase, expected.hidden(), run.durationMs(),
                passedCase ? null : failureMessage(run, expected, false),
                expected.hidden() ? null : "（题目自带的建表与数据）",
                expected.hidden() ? null : expected.expectedStdout(),
                expected.hidden() ? null : run.stdout()));
        String status = passedCase ? JudgeOutcome.PASSED
                : (run.compileOutput().isBlank() ? JudgeOutcome.FAILED : JudgeOutcome.ERROR);
        return JudgeOutcome.of(status, passedCase ? 1 : 0, 1, run.durationMs(),
                run.stdout(), run.stderr(), run.compileOutput(), results);
    }

    /** 出题阶段也要用它把参考查询跑一遍，所以放开包级可见。 */
    String sqlScript(String setup, String query) {
        // 用 base64 传 SQL，而不是把 JSON 转义后的文本塞进 Python 字面量：
        // Python 会先把它还原（\n 变成真实换行），json.loads 再收到带换行的非法 JSON 直接报错。
        // base64 全是 ASCII，没有转义歧义。
        String setupLiteral = base64(setup);
        String queryLiteral = base64(query);
        return """
                import base64, sqlite3, sys
                setup = base64.b64decode("%s").decode("utf-8")
                query = base64.b64decode("%s").decode("utf-8")
                con = sqlite3.connect(":memory:")
                try:
                    con.executescript(setup)
                    rows = con.execute(query).fetchall()
                except Exception as exc:
                    print(f"SQL 执行失败: {exc}", file=sys.stderr)
                    sys.exit(1)
                for row in rows:
                    print("\\t".join("NULL" if value is None else str(value) for value in row))
                """.formatted(setupLiteral, queryLiteral);
    }

    private static String base64(String value) {
        return java.util.Base64.getEncoder().encodeToString(
                (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ---------- 编程填空 ----------

    private JudgeOutcome judgeFillBlank(CodeQuestionEntity question, String answer) {
        List<Blank> blanks = readList(question.getBlanksJson(), Blank.class);
        if (blanks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这道题没有配置空位");
        }
        List<String> values = readList(answer, String.class);
        List<CaseResult> results = new ArrayList<>();
        int passed = 0;
        for (int index = 0; index < blanks.size(); index++) {
            Blank blank = blanks.get(index);
            String actual = index < values.size() ? values.get(index) : "";
            boolean ok = blank.accepted().stream()
                    .anyMatch(candidate -> normalizeAnswer(candidate).equals(normalizeAnswer(actual)));
            if (ok) passed++;
            results.add(new CaseResult(index + 1, "第 " + (index + 1) + " 空", ok, false, 0,
                    ok ? null : "该空不正确",
                    blank.hint(), null, ok ? null : actual));
        }
        return JudgeOutcome.of(passed == blanks.size() ? JudgeOutcome.PASSED : JudgeOutcome.FAILED,
                passed, blanks.size(), 0L, "", "", "", results);
    }

    // ---------- 选择题 ----------

    private JudgeOutcome judgeChoice(CodeQuestionEntity question, String answer) {
        Set<String> expected = new LinkedHashSet<>(readList(question.getAnswerJson(), String.class).stream()
                .map(item -> item.trim().toUpperCase()).toList());
        Set<String> actual = new LinkedHashSet<>(Arrays.stream(answer.split("[,，\\s]+"))
                .map(String::trim).filter(item -> !item.isEmpty())
                .map(item -> item.toUpperCase()).toList());
        if (expected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这道题没有配置答案");
        }
        boolean ok = expected.equals(actual);
        List<CaseResult> results = List.of(new CaseResult(1, "选项", ok, false, 0,
                ok ? null : "正确选项为 " + String.join(", ", expected), null, null,
                String.join(", ", actual)));
        return JudgeOutcome.of(ok ? JudgeOutcome.PASSED : JudgeOutcome.FAILED, ok ? 1 : 0, 1, 0L,
                "", "", "", results);
    }

    // ---------- 简答题 ----------

    private JudgeOutcome judgeShortAnswer(CodeQuestionEntity question, String answer) {
        if (answer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回答不能为空");
        }
        JudgeOutcome.ShortAnswerReview review =
                shortAnswerGrader.grade(question.getStatement(), question.getAnswerJson(), answer);
        boolean ok = review.score() >= SHORT_ANSWER_PASS_SCORE;
        return new JudgeOutcome(ok ? JudgeOutcome.PASSED : JudgeOutcome.FAILED,
                ok ? 1 : 0, 1, 0L, "", "", "", List.of(), review);
    }

    // ---------- 工具 ----------

    private <T> List<T> readList(String json, Class<T> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "题目数据损坏：" + exception.getMessage(), exception);
        }
    }

    /** 判题时忽略行尾空白与首尾空行，避免因为多余换行判错。 */
    static String normalizeOutput(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace("\r", "\n")
                .lines().map(String::stripTrailing).collect(Collectors.joining("\n")).strip();
    }

    /** 填空比对：忽略首尾与内部多余空白，但保留大小写（Java 关键字大小写有意义）。 */
    static String normalizeAnswer(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }
}
