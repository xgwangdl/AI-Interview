package com.guangge.Interview.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.coding.dto.JudgeOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 判题：确定性题型 + 上机题型（上机部分用 mock 的沙箱，不依赖 Docker）。 */
class CodeJudgeServiceTest {

    private CodeRunnerService runner;
    private CodeJudgeService judge;

    @BeforeEach
    void setUp() {
        runner = mock(CodeRunnerService.class);
        judge = new CodeJudgeService(runner, new ObjectMapper(),
                (question, points, answer) -> new JudgeOutcome.ShortAnswerReview(75, "基本答到要点", List.of("A"), List.of("B")));
    }

    @Test
    void choicePassesOnlyWhenTheSelectedSetMatches() {
        CodeQuestionEntity question = question(QuestionType.CHOICE, null);
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"a\"},{\"key\":\"B\",\"text\":\"b\"},{\"key\":\"C\",\"text\":\"c\"}]");
        // 必须用线上真实的存储格式：参考答案是对象 {"keys":[...]}，不是裸数组。
        question.setAnswerJson("{\"keys\":[\"A\",\"C\"]}");

        assertEquals(JudgeOutcome.PASSED, judge.judge(question, "A,C", false).status());
        assertEquals(JudgeOutcome.PASSED, judge.judge(question, "c, a", false).status(), "顺序与大小写不影响");
        assertEquals(JudgeOutcome.FAILED, judge.judge(question, "A", false).status(), "少选不算对");
        assertEquals(JudgeOutcome.FAILED, judge.judge(question, "A,B,C", false).status(), "多选不算对");
    }

    @Test
    void fillBlankIgnoresSurroundingWhitespaceButNotCase() {
        CodeQuestionEntity question = question(QuestionType.FILL_BLANK, null);
        question.setStarterCode("for (int i = 0; i < n; {{1}}) { }");
        question.setBlanksJson("""
                [{"index":0,"accepted":["i++","i = i + 1"],"hint":"循环步进"}]
                """);

        assertEquals(JudgeOutcome.PASSED, judge.judge(question, "[\"  i++  \"]", false).status());
        assertEquals(JudgeOutcome.PASSED, judge.judge(question, "[\"i = i + 1\"]", false).status());
        assertEquals(JudgeOutcome.FAILED, judge.judge(question, "[\"I++\"]", false).status(), "大小写要区分");
        assertEquals(JudgeOutcome.FAILED, judge.judge(question, "[\"i--\"]", false).status());
    }

    @Test
    void shortAnswerUsesTheGraderScore() {
        CodeQuestionEntity question = question(QuestionType.SHORT_ANSWER, null);
        question.setAnswerJson("{\"points\":[\"要点A\",\"要点B\"]}");

        JudgeOutcome outcome = judge.judge(question, "我的回答", false);

        assertEquals(JudgeOutcome.PASSED, outcome.status());
        assertEquals(75.0, outcome.review().score());
    }

    @Test
    void codingRunsEveryCaseAndOnlyExposesVisibleDetails() {
        CodeQuestionEntity question = question(QuestionType.CODING, CodeLanguage.PYTHON.id());
        question.setTestCasesJson("""
                [{"name":"示例","stdin":"1\\n","expectedStdout":"1","hidden":false},
                 {"name":"边界","stdin":"2\\n","expectedStdout":"2","hidden":true}]
                """);
        when(runner.run(eq(CodeLanguage.PYTHON), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String stdin = invocation.getArgument(2, String.class);
                    return new CodeRunResult(true, 0, stdin.trim(), "", "", 12, false);
                });

        JudgeOutcome outcome = judge.judge(question, "print(input())", false);

        assertEquals(JudgeOutcome.PASSED, outcome.status());
        assertEquals(2, outcome.passedCases());
        assertEquals(2, outcome.totalCases());
        assertTrue(outcome.cases().get(0).expected() != null, "可见用例给出期望输出");
        assertTrue(outcome.cases().get(1).expected() == null, "隐藏用例不能回传期望输出");
        assertTrue(outcome.cases().get(1).input() == null, "隐藏用例不能回传输入");
    }

    @Test
    void codingRunOnlyTouchesVisibleCases() {
        CodeQuestionEntity question = question(QuestionType.CODING, CodeLanguage.PYTHON.id());
        question.setTestCasesJson("""
                [{"name":"示例","stdin":"1","expectedStdout":"1","hidden":false},
                 {"name":"边界","stdin":"2","expectedStdout":"2","hidden":true}]
                """);
        when(runner.run(any(), anyString(), anyString()))
                .thenReturn(new CodeRunResult(true, 0, "1", "", "", 5, false));

        JudgeOutcome outcome = judge.judge(question, "code", true);

        assertEquals(1, outcome.totalCases(), "「运行」只跑可见用例");
    }

    @Test
    void codingReportsCompileErrorsAsErrorStatus() {
        CodeQuestionEntity question = question(QuestionType.CODING, CodeLanguage.JAVA.id());
        question.setTestCasesJson("""
                [{"name":"示例","stdin":"","expectedStdout":"1","hidden":false}]
                """);
        when(runner.run(any(), anyString(), anyString()))
                .thenReturn(new CodeRunResult(false, 1, "", "", "Main.java:2: error: ';' expected", 30, false));

        JudgeOutcome outcome = judge.judge(question, "broken", false);

        assertEquals(JudgeOutcome.ERROR, outcome.status());
        assertFalse(outcome.compileOutput().isBlank());
    }

    private static CodeQuestionEntity question(QuestionType type, String language) {
        CodeQuestionEntity question = new CodeQuestionEntity();
        question.setId(UUID.randomUUID());
        question.setSessionId(UUID.randomUUID());
        question.setType(type.id());
        question.setLanguage(language);
        question.setTitle("t");
        question.setStatement("s");
        return question;
    }
}
