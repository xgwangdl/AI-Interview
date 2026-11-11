package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guangge.Interview.quota.ModelCallGuard;
import com.guangge.Interview.quota.ModelLease;
import com.guangge.Interview.util.LogSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DebriefRunner {
    private final DebriefService debriefs;
    private final JobRadarAiGateway ai;
    private final DebriefParserValidator validator;
    private final JobAgentRunService runs;
    private final ModelCallGuard modelCalls;
    private final ObjectMapper objectMapper;
    private final int minutesSaved;

    public DebriefRunner(DebriefService debriefs, JobRadarAiGateway ai, DebriefParserValidator validator,
                         JobAgentRunService runs, ModelCallGuard modelCalls, ObjectMapper objectMapper,
                         @Value("${practice.job-radar.minutes.debrief:20}") int minutesSaved) {
        this.debriefs = debriefs;
        this.ai = ai;
        this.validator = validator;
        this.runs = runs;
        this.modelCalls = modelCalls;
        this.objectMapper = objectMapper;
        this.minutesSaved = Math.max(0, minutesSaved);
    }

    @Transactional
    public void parse(JobAgentRunEntity run) {
        InterviewDebriefEntity debrief = debriefs.requireOwned(run.getOwnerId(), run.getSubjectId());
        AgentStepRecorder steps = new AgentStepRecorder();
        ModelLease lease = modelCalls.tryAcquire(run.getOwnerId(), "面试回填解析");
        if (!lease.allowed()) {
            throw new JobRunDeferredException(lease.resetsAt() == null ? Instant.now().plusSeconds(3600) : lease.resetsAt(),
                    "今天的 AI 额度已用完，回填解析将在额度重置后继续");
        }
        int modelCallCount = 0;
        String status = "parsed";
        int dropped = 0;
        try (lease) {
            long started = System.currentTimeMillis();
            try {
                modelCallCount = 1;
                DebriefParseResult parsed = ai.parseDebrief(debrief.getRawText(), debrief.getRoundName(), debrief.getOutcome());
                DebriefParserValidator.ValidationResult validation = validator.validate(debrief.getRawText(), parsed);
                dropped = validation.dropped();
                status = validation.questions().isEmpty() ? "partial"
                        : dropped > 0 ? "partial" : "parsed";
                debriefs.applyParsedQuestions(debrief, validation.questions(), parsed.interviewerFocus(), status);
                steps.record("parse_debrief", System.currentTimeMillis() - started, true,
                        dropped > 0 ? "丢弃 " + dropped + " 条无法在原话中校验的问题" : null);
            } catch (RuntimeException exception) {
                status = "manual";
                debriefs.applyParsedQuestions(debrief, java.util.List.of(), java.util.List.of(), status);
                steps.record("parse_debrief", System.currentTimeMillis() - started, false,
                        "模型解析失败，转手动录入：" + LogSanitizer.sanitize(exception.getMessage()));
            }
        }
        ObjectNode output = objectMapper.createObjectNode();
        output.put("parseStatus", status);
        output.put("questionCount", debrief.getQuestionsJson() == null ? 0 : debrief.getQuestionsJson().size());
        output.put("dropped", dropped);
        runs.complete(run.getId(), output, steps.steps(), modelCallCount, minutesSaved);
    }
}
