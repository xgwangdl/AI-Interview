package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the question list stored on a practice session.
 *
 * 追问相关的小算法（谁是原题、问到第几轮了）也放在这里：题目列表只有一处定义，
 * 出题、追问、报告三边如果各写一份判断，迟早会给出互相矛盾的答案。
 */
@Component
public class PracticeQuestionCatalog {

    /** 追问题目的 category。id 里也带 "follow"，但判类型只认 category。 */
    public static final String FOLLOW_UP_CATEGORY = "follow_up";

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

    public static boolean isFollowUp(PracticeQuestion question) {
        return question != null && FOLLOW_UP_CATEGORY.equals(question.category());
    }

    /** 题目在列表里的位置；找不到给 -1（旧数据或客户端与服务端不一致时不做假设）。 */
    public static int indexOf(List<PracticeQuestion> questions, String questionId) {
        if (questionId == null) return -1;
        for (int index = 0; index < questions.size(); index++) {
            if (questionId.equals(questions.get(index).id())) return index;
        }
        return -1;
    }

    /**
     * 这道题所属的原题位置。
     *
     * 追问会被插在它所追问的那道题后面（不是追加到末尾），所以"往左找最近的非追问题目"
     * 就是原题；连续追问两层也照样成立。原题自己返回它自己的位置。
     */
    public static int rootIndexOf(List<PracticeQuestion> questions, int index) {
        if (index < 0 || index >= questions.size()) return -1;
        for (int cursor = index; cursor >= 0; cursor--) {
            if (!isFollowUp(questions.get(cursor))) return cursor;
        }
        return 0;
    }

    /**
     * 这一轮是同一道原题的第几轮追问（1 起；原题本身是 0）。
     * 数的是"原题与它之间（含它自己）的追问题目个数"，也就是追问链的深度。
     */
    public static int followUpRoundOf(List<PracticeQuestion> questions, int index) {
        int root = rootIndexOf(questions, index);
        if (root < 0) return 0;
        int round = 0;
        for (int cursor = root + 1; cursor <= index && cursor < questions.size(); cursor++) {
            if (isFollowUp(questions.get(cursor))) round++;
        }
        return round;
    }

    /** 追问 id → 原题 id。报告页靠它把追问挂在原来的那道题下面。 */
    public static Map<String, String> followUpRoots(List<PracticeQuestion> questions) {
        Map<String, String> roots = new HashMap<>();
        String rootId = null;
        for (PracticeQuestion question : questions) {
            if (isFollowUp(question)) {
                if (rootId != null) roots.put(question.id(), rootId);
            } else {
                rootId = question.id();
            }
        }
        return roots;
    }
}
