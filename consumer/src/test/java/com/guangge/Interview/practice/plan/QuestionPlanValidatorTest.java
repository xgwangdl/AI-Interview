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
}
