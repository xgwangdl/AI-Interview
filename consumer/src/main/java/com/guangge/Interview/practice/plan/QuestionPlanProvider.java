package com.guangge.Interview.practice.plan;

import com.guangge.Interview.practice.dto.PracticeQuestion;

import java.util.List;

public interface QuestionPlanProvider {

    List<PracticeQuestion> plan(QuestionPlanRequest request);

    String provider();

    String model();

    String promptVersion();
}
