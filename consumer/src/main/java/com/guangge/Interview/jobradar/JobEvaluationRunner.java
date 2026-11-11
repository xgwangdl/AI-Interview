package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guangge.Interview.jobradar.JobRadarDomain.MatchInput;
import com.guangge.Interview.jobradar.JobRadarDomain.MatchResult;
import com.guangge.Interview.jobradar.JobRadarDomain.Skill;
import com.guangge.Interview.quota.ModelCallGuard;
import com.guangge.Interview.quota.ModelLease;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JobEvaluationRunner {
    private static final Logger log = LoggerFactory.getLogger(JobEvaluationRunner.class);

    private final JobLeadRepository leads;
    private final JobEvaluationRepository evaluations;
    private final JobAgentRunService runService;
    private final SkillProfileService profiles;
    private final MatchScorer scorer;
    private final JobRadarAiGateway ai;
    private final RuleBasedSkillExtractor fallbackExtractor;
    private final JobLeadStatusMachine statusMachine;
    private final ModelCallGuard modelCalls;
    private final ObjectMapper objectMapper;
    private final String promptVersion;
    private final int minutesSaved;

    public JobEvaluationRunner(
            JobLeadRepository leads,
            JobEvaluationRepository evaluations,
            JobAgentRunService runService,
            SkillProfileService profiles,
            MatchScorer scorer,
            JobRadarAiGateway ai,
            RuleBasedSkillExtractor fallbackExtractor,
            JobLeadStatusMachine statusMachine,
            ModelCallGuard modelCalls,
            ObjectMapper objectMapper,
            @Value("${practice.job-radar.prompt-version:v1}") String promptVersion,
            @Value("${practice.job-radar.minutes.job-evaluate:15}") int minutesSaved) {
        this.leads = leads;
        this.evaluations = evaluations;
        this.runService = runService;
        this.profiles = profiles;
        this.scorer = scorer;
        this.ai = ai;
        this.fallbackExtractor = fallbackExtractor;
        this.statusMachine = statusMachine;
        this.modelCalls = modelCalls;
        this.objectMapper = objectMapper;
        this.promptVersion = promptVersion;
        this.minutesSaved = Math.max(0, minutesSaved);
    }

    @Transactional
    public void evaluate(JobAgentRunEntity run) {
        JobLeadEntity lead = leads.findByIdAndOwnerId(run.getSubjectId(), run.getOwnerId())
                .orElseThrow(() -> new IllegalStateException("岗位已删除"));
        if (lead.getStatus().equals(JobLeadStatusMachine.ARCHIVED)) {
            throw new IllegalStateException("归档岗位不能评估");
        }

        AgentStepRecorder steps = new AgentStepRecorder();
        JobRadarDomain.SkillProfile profile = profiles.load(run.getOwnerId());
        boolean degraded = false;
        int modelCallCount = 0;

        JdSkillExtraction extraction;
        ModelLease extractLease = modelCalls.tryAcquire(run.getOwnerId(), "岗位技能抽取");
        if (!extractLease.allowed()) {
            throw new JobRunDeferredException(resumeAt(extractLease), "今天的 AI 额度已用完，任务将在额度重置后继续");
        }
        try (extractLease) {
            long started = System.currentTimeMillis();
            try {
                extraction = ai.extractJdSkills(lead.getJdText());
                modelCallCount++;
                steps.record("extract_jd_skills", System.currentTimeMillis() - started, true, null);
            } catch (RuntimeException exception) {
                degraded = true;
                extraction = fallbackExtractor.extract(lead.getJdText());
                steps.record("extract_jd_skills", System.currentTimeMillis() - started, false,
                        "模型抽取失败，使用规则抽取：" + safe(exception.getMessage()));
            }
        }
        if (extraction == null || extraction.required().isEmpty()) {
            degraded = true;
            extraction = fallbackExtractor.extract(lead.getJdText());
        }

        MatchResult result = score(lead, profile, extraction);
        EvaluationExplanation explanation = new EvaluationExplanation(templateReason(result), List.of());
        ModelLease explainLease = modelCalls.tryAcquire(run.getOwnerId(), "岗位评估解释");
        if (explainLease.allowed()) {
            try (explainLease) {
                long started = System.currentTimeMillis();
                try {
                    explanation = ai.explainEvaluation(lead.getRole(), lead.getCompany(), result);
                    modelCallCount++;
                    steps.record("explain_evaluation", System.currentTimeMillis() - started, true, null);
                } catch (RuntimeException exception) {
                    degraded = true;
                    steps.record("explain_evaluation", System.currentTimeMillis() - started, false,
                            "模型解释失败，使用模板：" + safe(exception.getMessage()));
                }
            }
        } else {
            degraded = true;
            steps.record("explain_evaluation", 0, false, "额度不足，使用规则解释");
        }

        JobEvaluationEntity evaluation = persist(lead, profile, result, explanation, degraded);
        String previous = text(run.getInputJson(), "previousStatus");
        lead.setStatus(statusMachine.finishEvaluation(previous, result.score(), result.recommendation()));
        lead.setUpdatedAt(Instant.now());
        leads.save(lead);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("evaluationId", evaluation.getId().toString());
        output.put("score", result.score());
        output.put("recommendation", result.recommendation());
        output.put("degraded", degraded);
        output.set("topActions", objectMapper.valueToTree(explanation.topActions()));
        runService.complete(run.getId(), output, steps.steps(), modelCallCount, minutesSaved);
        log.info("岗位评估完成 owner={} lead={} score={} degraded={} modelCalls={}",
                run.getOwnerId(), lead.getId(), result.score(), degraded, modelCallCount);
    }

    @Transactional
    public void recoverTerminalFailure(JobAgentRunEntity run) {
        if (!JobAgentRunService.KIND_JOB_EVALUATE.equals(run.getKind()) || run.getSubjectId() == null) return;
        leads.findByIdAndOwnerId(run.getSubjectId(), run.getOwnerId()).ifPresent(lead -> {
            String current = lead.getStatus();
            String restored = statusMachine.recoverFailedEvaluation(current, text(run.getInputJson(), "previousStatus"));
            if (!restored.equals(current)) {
                lead.setStatus(restored);
                lead.setUpdatedAt(Instant.now());
                leads.save(lead);
            }
        });
    }

    private MatchResult score(JobLeadEntity lead, JobRadarDomain.SkillProfile profile, JdSkillExtraction extraction) {
        List<Skill> required = extraction.required().stream()
                .map(skill -> new Skill(skill.name(), skill.level(), skill.weight())).toList();
        List<Skill> nice = extraction.niceToHave().stream()
                .map(skill -> new Skill(skill.name(), skill.level(), skill.weight())).toList();
        String userCity = profile == null ? null : profile.city();
        return scorer.score(new MatchInput(lead.getJdText(), required, nice, profile, extraction.years(),
                lead.getSalaryMinK(), lead.getSalaryMaxK(), null, null,
                extraction.city() == null ? lead.getCity() : extraction.city(), userCity,
                extraction.remoteFriendly()));
    }

    private JobEvaluationEntity persist(JobLeadEntity lead, JobRadarDomain.SkillProfile profile,
                                        MatchResult result, EvaluationExplanation explanation, boolean degraded) {
        String profileHash = profiles.hash(profile);
        JobEvaluationEntity evaluation = evaluations
                .findFirstByJobLeadIdAndJdHashAndProfileHashAndPromptVersionOrderByCreatedAtDesc(
                        lead.getId(), lead.getJdHash(), profileHash, promptVersion)
                .orElseGet(JobEvaluationEntity::new);
        if (evaluation.getId() == null) evaluation.setId(UUID.randomUUID());
        evaluation.setOwnerId(lead.getOwnerId());
        evaluation.setJobLeadId(lead.getId());
        evaluation.setJdHash(lead.getJdHash());
        evaluation.setProfileHash(profileHash);
        evaluation.setPromptVersion(promptVersion);
        evaluation.setScore(result.score());
        evaluation.setConfidence(result.confidence());
        evaluation.setBreakdownJson(objectMapper.valueToTree(result.breakdown()));
        evaluation.setHitsJson(objectMapper.valueToTree(result.hits()));
        evaluation.setGapsJson(objectMapper.valueToTree(result.gaps()));
        evaluation.setReason(trimReason(explanation.reason()));
        evaluation.setRecommendation(result.recommendation());
        evaluation.setDegraded(degraded);
        evaluation.setModel(ai.model());
        evaluation.setCreatedAt(Instant.now());
        return evaluations.save(evaluation);
    }

    private static String templateReason(MatchResult result) {
        int total = result.hits().size();
        int gaps = result.gaps().size();
        return "你的技能覆盖了 " + (total - gaps) + "/" + total + " 项要求。"
                + (gaps == 0 ? "主要要求已经覆盖，建议结合项目经历继续准备。" : "先补齐权重较高的缺口，再决定是否投递。");
    }

    private static String trimReason(String reason) {
        if (reason == null || reason.isBlank()) return "本次评估已完成。";
        String value = reason.strip();
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private static Instant resumeAt(ModelLease lease) {
        return lease.resetsAt() == null ? Instant.now().plusSeconds(3600) : lease.resetsAt();
    }

    private static String safe(String message) {
        return LogSanitizer.sanitize(message == null ? "未知错误" : message);
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() || node.path(field).isNull()
                ? null : node.path(field).asText();
    }
}
