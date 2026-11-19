package com.guangge.Interview.practice.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPlanValidatorTest {

    private final QuestionPlanValidator validator =
            new QuestionPlanValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void assignsServerSideIdsAndDefaultsMissingCategory() {
        List<PracticeQuestion> questions = validator.parse("""
                [
                  { "text": "请介绍一个你主导的项目。", "category": "项目设计" },
                  { "text": "线上接口变慢你会怎么排查？" }
                ]
                """, 2);

        assertEquals(2, questions.size());
        assertEquals("q-1", questions.get(0).id());
        assertEquals("项目设计", questions.get(0).category());
        assertEquals("q-2", questions.get(1).id());
        assertEquals("综合", questions.get(1).category());
    }

    @Test
    void ignoresModelSuppliedIds() {
        List<PracticeQuestion> questions = validator.parse(
                "[{ \"id\": \"duplicate\", \"text\": \"第一题\" }, { \"id\": \"duplicate\", \"text\": \"第二题\" }]", 2);

        assertEquals("q-1", questions.get(0).id());
        assertEquals("q-2", questions.get(1).id());
    }

    @Test
    void acceptsResponsesWrappedInCodeFences() {
        List<PracticeQuestion> questions =
                validator.parse("```json\n[{ \"text\": \"只有一道题\" }]\n```", 1);

        assertEquals("只有一道题", questions.get(0).text());
    }

    @Test
    void rejectsUnexpectedQuestionCount() {
        QuestionPlanException error = assertThrows(QuestionPlanException.class,
                () -> validator.parse("[{ \"text\": \"只有一道题\" }]", 5));

        assertTrue(error.getMessage().contains("期望 5 道"), error.getMessage());
    }

    @Test
    void rejectsBlankQuestionText() {
        assertThrows(QuestionPlanException.class,
                () -> validator.parse("[{ \"text\": \"   \" }]", 1));
    }

    @Test
    void rejectsDuplicateQuestions() {
        assertThrows(QuestionPlanException.class,
                () -> validator.parse("[{ \"text\": \"重复\" }, { \"text\": \"重复\" }]", 2));
    }

    @Test
    void rejectsFreeFormModelOutput() {
        assertThrows(QuestionPlanException.class, () -> validator.parse("好的，我来出几道题：", 3));
    }

    @Test
    void technicalInterviewRequiresEnoughConcreteEngineeringQuestions() {
        String plan = """
                [
                  {"text":"请介绍一次你解决过的线上接口性能问题。","category":"问题排查"},
                  {"text":"Spring 事务在什么情况下会失效？","category":"技术原理"},
                  {"text":"MySQL 联合索引为什么需要遵守最左前缀？","category":"技术原理"},
                  {"text":"当你和产品经理意见不一致时怎么处理？","category":"协作沟通"},
                  {"text":"你未来三个月有什么计划？","category":"岗位匹配"}
                ]
                """;

        List<PracticeQuestion> questions = validator.parse(plan, 5, "technical");

        assertEquals(5, questions.size());
        assertEquals("Spring 事务在什么情况下会失效？", questions.get(1).text());
    }

    @Test
    void technicalInterviewRejectsAPlanDominatedByGenericSoftQuestions() {
        String plan = """
                [
                  {"text":"请介绍一次你解决过的问题。","category":"项目经验"},
                  {"text":"当需求不明确时你怎么安排优先级？","category":"问题解决"},
                  {"text":"分享一次你和同事意见不一致的经历。","category":"协作沟通"},
                  {"text":"你希望加入什么样的团队？","category":"岗位匹配"},
                  {"text":"你未来三个月有什么计划？","category":"职业规划"}
                ]
                """;

        QuestionPlanException error = assertThrows(QuestionPlanException.class,
                () -> validator.parse(plan, 5, "technical"));

        assertTrue(error.getMessage().contains("技术题"), error.getMessage());
    }

    @Test
    void behavioralInterviewCanUseSoftSkillQuestions() {
        String plan = """
                [
                  {"text":"请介绍一次你推动团队达成目标的经历。","category":"项目经验"},
                  {"text":"需求不明确时你如何澄清？","category":"问题解决"},
                  {"text":"分享一次意见不一致的处理经历。","category":"协作沟通"}
                ]
                """;

        assertEquals(3, validator.parse(plan, 3, "behavioral").size());
    }
}
