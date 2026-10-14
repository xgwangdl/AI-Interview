package com.guangge.Interview.coding;

import com.guangge.Interview.coding.dto.JudgeOutcome;

/** 简答题评分（没有唯一答案，只能按参考要点让模型评）。 */
public interface ShortAnswerGrader {

    JudgeOutcome.ShortAnswerReview grade(String question, String referencePoints, String answer);
}
