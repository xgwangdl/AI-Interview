package com.guangge.Interview.coding.dto;

import java.util.List;
import java.util.UUID;

/**
 * 下发给前端的题目视图。
 *
 * 注意：这里**不能**带 any 参考答案 —— 填空的 accepted、选择的正确项、上机/SQL 的参考解
 * 都只在服务端使用（{@code CodeQuestionEntity#getAnswerJson()}）。
 */
public record CodeQuestionView(
        UUID id,
        int position,
        String type,
        String language,
        String title,
        String statement,
        String starterCode,
        List<BlankView> blanks,
        List<ChoiceOption> options,
        List<TestCase> visibleTestCases,
        String sqlSetup,
        String difficulty,
        String tags) {

    /** 填空的一空：只给序号和提示，不给答案。 */
    public record BlankView(int index, String hint) { }
}
