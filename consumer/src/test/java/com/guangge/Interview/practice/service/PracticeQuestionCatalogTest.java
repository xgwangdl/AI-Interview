package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 追问链的算法。
 *
 * 追问是插在**它追问的那道题后面**的，所以"谁是谁的原题"只能靠位置推 ——
 * 这里就是那套推法的唯一实现，出题、追问、报告三边都指着它。
 */
class PracticeQuestionCatalogTest {

    private final PracticeQuestionCatalog catalog = new PracticeQuestionCatalog(new ObjectMapper());

    /** 一场练习加了两次追问之后的样子。 */
    private static final List<PracticeQuestion> QUESTIONS = List.of(
            question("q-1", "讲一个你优化过的接口", "technical"),
            question("q-0-follow-1", "你说的优化具体改了什么？", "follow_up"),
            question("q-1-follow-2", "合并之后有没有引入新问题？", "follow_up"),
            question("q-2", "讲讲你的系统设计", "system"));

    @Test
    @DisplayName("原题就是左边最近的那个非追问题目")
    void findsTheRootQuestion() {
        assertThat(PracticeQuestionCatalog.rootIndexOf(QUESTIONS, 0)).isZero();
        assertThat(PracticeQuestionCatalog.rootIndexOf(QUESTIONS, 1)).isZero();
        assertThat(PracticeQuestionCatalog.rootIndexOf(QUESTIONS, 2)).isZero();
        // 追问问完之后才是下一道正题：它自己是原题
        assertThat(PracticeQuestionCatalog.rootIndexOf(QUESTIONS, 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("第几轮追问：原题是 0，第一个追问是 1，同一个话题继续问才是 2")
    void countsRoundsWithinOneTopic() {
        assertThat(PracticeQuestionCatalog.followUpRoundOf(QUESTIONS, 0)).isZero();
        assertThat(PracticeQuestionCatalog.followUpRoundOf(QUESTIONS, 1)).isEqualTo(1);
        assertThat(PracticeQuestionCatalog.followUpRoundOf(QUESTIONS, 2)).isEqualTo(2);
        assertThat(PracticeQuestionCatalog.followUpRoundOf(QUESTIONS, 3)).isZero();
    }

    @Test
    @DisplayName("报告要的映射：追问 → 原题 id，原题自己不出现在表里")
    void mapsFollowUpsToTheirRoot() {
        Map<String, String> roots = PracticeQuestionCatalog.followUpRoots(QUESTIONS);

        assertThat(roots).containsEntry("q-0-follow-1", "q-1").containsEntry("q-1-follow-2", "q-1");
        assertThat(roots).doesNotContainKey("q-1");
    }

    @Test
    @DisplayName("读不出题目、位置越界都不抛异常：报告和追问都不该因为脏数据挂掉")
    void toleratesBrokenInput() {
        assertThat(PracticeQuestionCatalog.rootIndexOf(QUESTIONS, -1)).isEqualTo(-1);
        assertThat(PracticeQuestionCatalog.rootIndexOf(QUESTIONS, 99)).isEqualTo(-1);
        assertThat(PracticeQuestionCatalog.followUpRoundOf(QUESTIONS, 99)).isZero();
        assertThat(PracticeQuestionCatalog.indexOf(QUESTIONS, "q-404")).isEqualTo(-1);
        assertThat(catalog.questionText("[{\"id\":\"q-1\",\"text\":\"问一句\"}]", "q-1")).isEqualTo("问一句");
    }

    private static PracticeQuestion question(String id, String text, String category) {
        return new PracticeQuestion(id, text, category);
    }
}
