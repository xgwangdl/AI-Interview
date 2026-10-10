package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reads the question list stored on a practice session. */
@Component
public class PracticeQuestionCatalog {

    private static final TypeReference<List<PracticeQuestion>> QUESTIONS_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public PracticeQuestionCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PracticeQuestion> questions(String questionsJson) {
        try {
            return objectMapper.readValue(questionsJson, QUESTIONS_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the session question list", exception);
        }
    }

    public String questionText(String questionsJson, String questionId) {
        return questions(questionsJson).stream()
                .filter(question -> question.id().equals(questionId))
                .map(PracticeQuestion::text)
                .findFirst()
                .orElse("");
    }
}
