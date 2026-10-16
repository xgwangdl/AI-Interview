package com.guangge.Interview.practice.plan;

import com.guangge.Interview.practice.dto.PracticeQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionPlanServiceTest {

    private static final QuestionPlanRequest REQUEST = new QuestionPlanRequest(
            "Java 后端", "Light", "负责订单系统", "resume.pdf", "五年 Java 后端经验",
            "technical", "standard", "balanced", 3, null);

    @Test
    void returnsQuestionsWithTheirProvenance() {
        QuestionPlanService service = new QuestionPlanService(stubProvider());

        QuestionPlanView view = service.plan(REQUEST);

        assertEquals("spring-ai", view.provider());
        assertEquals("qwen-max", view.model());
        assertEquals("practice-question-plan-v1", view.promptVersion());
        assertEquals(3, view.questions().size());
    }

    @Test
    void signalsServiceUnavailableSoTheClientCanFallBack() {
        QuestionPlanProvider failing = new QuestionPlanProvider() {
            @Override
            public List<PracticeQuestion> plan(QuestionPlanRequest request) {
                throw new QuestionPlanException("模型返回的不是题目 JSON 数组");
            }

            @Override
            public String provider() {
                return "spring-ai";
            }

            @Override
            public String model() {
                return "qwen-max";
            }

            @Override
            public String promptVersion() {
                return "practice-question-plan-v1";
            }
        };

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new QuestionPlanService(failing).plan(REQUEST));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    private static QuestionPlanProvider stubProvider() {
        return new QuestionPlanProvider() {
            @Override
            public List<PracticeQuestion> plan(QuestionPlanRequest request) {
                return List.of(
                        new PracticeQuestion("q-1", "第一题", "技术原理"),
                        new PracticeQuestion("q-2", "第二题", "系统设计"),
                        new PracticeQuestion("q-3", "第三题", "工程实践"));
            }

            @Override
            public String provider() {
                return "spring-ai";
            }

            @Override
            public String model() {
                return "qwen-max";
            }

            @Override
            public String promptVersion() {
                return "practice-question-plan-v1";
            }
        };
    }
}
