package com.guangge.Interview.jobradar;

public interface JobRadarAiGateway {
    JdSkillExtraction extractJdSkills(String jdText);

    EvaluationExplanation explainEvaluation(String role, String company, JobRadarDomain.MatchResult result);

    DebriefParseResult parseDebrief(String rawText, String roundName, String outcome);

    String model();
}
