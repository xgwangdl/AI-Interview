package com.guangge.Interview.practice.plan;

import com.guangge.Interview.practice.dto.PracticeQuestion;

import java.util.List;

/** 出题结果及来源，方便前端标注「AI 生成」还是「内置题库」。 */
public record QuestionPlanView(
        String provider,
        String model,
        String promptVersion,
        List<PracticeQuestion> questions) { }
