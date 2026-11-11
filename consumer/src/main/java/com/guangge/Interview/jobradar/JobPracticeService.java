package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.plan.QuestionPlanRequest;
import com.guangge.Interview.practice.plan.QuestionPlanService;
import com.guangge.Interview.practice.plan.QuestionPlanView;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.quota.ModelCallGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobPracticeService {
    private final JobLeadService leads;
    private final JobEvaluationRepository evaluations;
    private final QuestionPlanService questionPlans;
    private final PracticeSessionService practiceSessions;
    private final JobAgentRunService runs;
    private final ModelCallGuard modelCalls;
    private final ObjectMapper objectMapper;

    public JobPracticeService(JobLeadService leads, JobEvaluationRepository evaluations,
                              QuestionPlanService questionPlans, PracticeSessionService practiceSessions,
                              JobAgentRunService runs, ModelCallGuard modelCalls, ObjectMapper objectMapper) {
        this.leads = leads;
        this.evaluations = evaluations;
        this.questionPlans = questionPlans;
        this.practiceSessions = practiceSessions;
        this.runs = runs;
        this.modelCalls = modelCalls;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobPracticeResponse create(String ownerId, java.util.UUID leadId, JobPracticeRequest request) {
        JobLeadEntity lead = leads.requireOwned(ownerId, leadId);
        int count = request.questionCount() <= 0 ? 3 : request.questionCount();
        JobEvaluationEntity evaluation = evaluations
                .findFirstByJobLeadIdAndOwnerIdOrderByCreatedAtDesc(leadId, ownerId).orElse(null);
        List<String> gapTopics = gapTopics(evaluation);
        String focusText = gapTopics.isEmpty() ? "" : "\n\n本次出题优先覆盖这些已识别缺口：" + String.join("、", gapTopics);
        QuestionPlanRequest planRequest = new QuestionPlanRequest(lead.getRole(), lead.getCompany(),
                lead.getJdText() + focusText, null, null, "technical",
                request.difficulty() == null ? "standard" : request.difficulty(),
                request.interviewerStyle() == null ? "balanced" : request.interviewerStyle(), count,
                List.of("professionalism", "completeness"));
        QuestionPlanView plan = modelCalls.call(ownerId, "岗位定制出题", () -> questionPlans.plan(planRequest));
        List<PracticeQuestion> questions = plan.questions();
        if (questions.size() != count) {
            throw new IllegalStateException("AI 出题数量与请求不一致");
        }
        PracticeSessionRequest sessionRequest = new PracticeSessionRequest(lead.getRole(), lead.getCompany(),
                lead.getJdText(), null, null, "technical",
                request.difficulty() == null ? "standard" : request.difficulty(),
                request.interviewerStyle() == null ? "balanced" : request.interviewerStyle(), count,
                Math.max(10, count * 4), false, "per_question", questions);
        var session = practiceSessions.create(ownerId, sessionRequest);
        String idem = JobRadarHashing.sha256(ownerId + "|practice-from-jd|" + leadId + "|" + session.id());
        var input = objectMapper.createObjectNode();
        input.put("sessionId", session.id().toString());
        var run = runs.enqueue(ownerId, JobAgentRunService.KIND_PRACTICE_FROM_JD, "job_lead",
                leadId, idem, input).run();
        var output = objectMapper.createObjectNode();
        output.put("sessionId", session.id().toString());
        output.put("questionCount", count);
        runs.complete(run.getId(), output, List.of(new AgentStep("generate_questions", 0, true, null)), 1, 10);
        return new JobPracticeResponse(session.id(), questions, gapTopics);
    }

    private List<String> gapTopics(JobEvaluationEntity evaluation) {
        if (evaluation == null || evaluation.getGapsJson() == null) return List.of();
        List<String> topics = new ArrayList<>();
        for (JsonNode gap : evaluation.getGapsJson()) {
            String name = gap.path("name").asText("");
            if (!name.isBlank()) topics.add(name);
            if (topics.size() == 3) break;
        }
        return List.copyOf(topics);
    }
}
