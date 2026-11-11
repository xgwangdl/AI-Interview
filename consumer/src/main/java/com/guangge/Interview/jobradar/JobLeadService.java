package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class JobLeadService {
    private final JobLeadRepository leads;
    private final JobEvaluationRepository evaluations;
    private final JobAgentRunRepository runs;
    private final JobAgentRunService runService;
    private final JobLeadStatusMachine statusMachine;
    private final SkillProfileService skillProfiles;
    private final ObjectMapper objectMapper;
    private final int dailyLimit;
    private final ZoneId zone;
    private final String promptVersion;

    public JobLeadService(
            JobLeadRepository leads,
            JobEvaluationRepository evaluations,
            JobAgentRunRepository runs,
            JobAgentRunService runService,
            JobLeadStatusMachine statusMachine,
            SkillProfileService skillProfiles,
            ObjectMapper objectMapper,
            @Value("${practice.job-radar.daily-limit:5}") int dailyLimit,
            @Value("${practice.job-radar.zone:Asia/Shanghai}") String zone,
            @Value("${practice.job-radar.prompt-version:v1}") String promptVersion) {
        this.leads = leads;
        this.evaluations = evaluations;
        this.runs = runs;
        this.runService = runService;
        this.statusMachine = statusMachine;
        this.skillProfiles = skillProfiles;
        this.objectMapper = objectMapper;
        this.dailyLimit = Math.max(1, dailyLimit);
        this.zone = ZoneId.of(zone);
        this.promptVersion = promptVersion;
    }

    @Transactional
    public JobLeadCreateResponse create(String ownerId, JobLeadCreateRequest request) {
        String jdText = JobRadarHashing.normalizeText(request.jdText());
        validateJd(jdText);
        validateSalary(request.salaryMinK(), request.salaryMaxK());
        String role = request.role().trim();
        String company = trimToNull(request.company());
        String jdHash = JobRadarHashing.sha256(jdText);
        String dedupKey = dedupKey(company, role, jdHash);

        Optional<JobLeadEntity> existing = leads.findByOwnerIdAndDedupKey(ownerId, dedupKey);
        if (existing.isPresent()) {
            JobLeadEntity duplicate = existing.get();
            UUID activeRun = runs.findFirstByOwnerIdAndKindAndSubjectIdAndStatusInOrderByCreatedAtDesc(
                            ownerId, JobAgentRunService.KIND_JOB_EVALUATE, duplicate.getId(),
                            List.of(JobAgentRunService.STATUS_PENDING, JobAgentRunService.STATUS_RUNNING,
                                    JobAgentRunService.STATUS_DEFERRED))
                    .map(JobAgentRunEntity::getId).orElse(null);
            return new JobLeadCreateResponse(duplicate.getId(), activeRun, duplicate.getStatus(), true);
        }

        enforceDailyLimit(ownerId);
        Instant now = Instant.now();
        JobLeadEntity lead = new JobLeadEntity();
        lead.setId(UUID.randomUUID());
        lead.setOwnerId(ownerId);
        lead.setCompany(company);
        lead.setRole(role);
        lead.setCity(trimToNull(request.city()));
        lead.setSalaryMinK(request.salaryMinK());
        lead.setSalaryMaxK(request.salaryMaxK());
        lead.setJdText(jdText);
        lead.setJdHash(jdHash);
        lead.setDedupKey(dedupKey);
        lead.setSource("manual_paste");
        lead.setStatus(JobLeadStatusMachine.NEW);
        lead.setSourceUrl(trimToNull(request.sourceUrl()));
        lead.setCreatedAt(now);
        lead.setUpdatedAt(now);
        leads.save(lead);

        JobAgentRunEntity run = enqueueEvaluation(ownerId, lead, false);
        return new JobLeadCreateResponse(lead.getId(), run.getId(), lead.getStatus(), false);
    }

    @Transactional(readOnly = true)
    public JobLeadPageView list(String ownerId, String status, String role, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(50, limit));
        String statusFilter = trimToNull(status);
        if (statusFilter != null && !JobLeadStatusMachine.ALL.contains(statusFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知岗位状态：" + statusFilter);
        }
        Page<JobLeadEntity> page = leads.search(ownerId, statusFilter, trimToNull(role),
                PageRequest.of(safeOffset / safeLimit, safeLimit));
        List<JobLeadSummaryView> items = page.getContent().stream().map(lead -> {
            JobEvaluationEntity evaluation = latestEvaluation(ownerId, lead.getId()).orElse(null);
            return new JobLeadSummaryView(lead.getId(), lead.getCompany(), lead.getRole(), lead.getCity(),
                    lead.getSalaryMinK(), lead.getSalaryMaxK(), lead.getStatus(),
                    evaluation == null ? null : evaluation.getScore(),
                    evaluation == null ? null : evaluation.getRecommendation(),
                    latestRunStatus(ownerId, lead.getId()), lead.getCreatedAt(), lead.getUpdatedAt());
        }).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        JobLeadStatusMachine.ALL.forEach(value -> counts.put(value, 0L));
        for (Object[] row : leads.countByStatus(ownerId)) {
            counts.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return new JobLeadPageView(items, page.getTotalElements(), safeOffset, safeLimit, counts);
    }

    @Transactional(readOnly = true)
    public JobLeadView get(String ownerId, UUID id) {
        JobLeadEntity lead = requireOwned(ownerId, id);
        return toDetailView(lead);
    }

    @Transactional
    public JobLeadView patch(String ownerId, UUID id, JobLeadPatchRequest request) {
        JobLeadEntity lead = requireOwned(ownerId, id);
        String previousCompany = lead.getCompany();
        String previousRole = lead.getRole();
        if (request.company() != null) lead.setCompany(trimToNull(request.company()));
        if (request.role() != null && !request.role().isBlank()) lead.setRole(request.role().trim());
        if (request.city() != null) lead.setCity(trimToNull(request.city()));
        if (request.salaryMinK() != null) lead.setSalaryMinK(request.salaryMinK());
        if (request.salaryMaxK() != null) lead.setSalaryMaxK(request.salaryMaxK());
        validateSalary(lead.getSalaryMinK(), lead.getSalaryMaxK());
        if (request.note() != null) lead.setNote(trimToNull(request.note()));
        if (request.status() != null && !request.status().isBlank()) {
            String target = request.status().trim();
            lead.setStatus(statusMachine.transitionByUser(lead.getStatus(), target));
            lead.setArchivedAt(JobLeadStatusMachine.ARCHIVED.equals(target) ? Instant.now() : null);
        }
        if (!java.util.Objects.equals(previousCompany, lead.getCompany())
                || !java.util.Objects.equals(previousRole, lead.getRole())) {
            String nextDedupKey = dedupKey(lead.getCompany(), lead.getRole(), lead.getJdHash());
            leads.findByOwnerIdAndDedupKey(ownerId, nextDedupKey)
                    .filter(existing -> !existing.getId().equals(lead.getId()))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "已存在公司、岗位和 JD 都相同的求职记录");
                    });
            lead.setDedupKey(nextDedupKey);
        }
        lead.setUpdatedAt(Instant.now());
        try {
            leads.save(lead);
        } catch (DataIntegrityViolationException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "已存在公司、岗位和 JD 都相同的求职记录", conflict);
        }
        return toDetailView(lead);
    }

    @Transactional
    public JobEvaluationRunResponse evaluate(String ownerId, UUID id, boolean force) {
        JobLeadEntity lead = requireOwned(ownerId, id);
        String profileHash = skillProfiles.profileHash(ownerId);
        if (!force) {
            Optional<JobEvaluationEntity> reused = evaluations
                    .findFirstByJobLeadIdAndJdHashAndProfileHashAndPromptVersionOrderByCreatedAtDesc(
                            lead.getId(), lead.getJdHash(), profileHash, promptVersion);
            if (reused.isPresent()) {
                return new JobEvaluationRunResponse(lead.getId(), null, "succeeded", true, toEvaluationView(reused.get()));
            }
        }
        Optional<JobAgentRunEntity> active = runs
                .findFirstByOwnerIdAndKindAndSubjectIdAndStatusInOrderByCreatedAtDesc(
                        ownerId, JobAgentRunService.KIND_JOB_EVALUATE, lead.getId(),
                        List.of(JobAgentRunService.STATUS_PENDING, JobAgentRunService.STATUS_RUNNING,
                                JobAgentRunService.STATUS_DEFERRED));
        if (active.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "正在评估中，请稍等");
        }
        enforceDailyLimit(ownerId);
        JobAgentRunEntity run = enqueueEvaluation(ownerId, lead, force);
        return new JobEvaluationRunResponse(lead.getId(), run.getId(), lead.getStatus(), false, null);
    }

    @Transactional
    public void delete(String ownerId, UUID id) {
        JobLeadEntity lead = requireOwned(ownerId, id);
        runService.cancelForDeletedSubject(id);
        evaluations.deleteAllByJobLeadId(id);
        leads.delete(lead);
    }

    @Transactional(readOnly = true)
    public JobLeadEntity requireOwned(String ownerId, UUID id) {
        return leads.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在"));
    }

    private JobAgentRunEntity enqueueEvaluation(String ownerId, JobLeadEntity lead, boolean force) {
        String previousStatus = lead.getStatus();
        if (!JobLeadStatusMachine.EVALUATING.equals(previousStatus)) {
            lead.setStatus(statusMachine.transitionByWorker(previousStatus, JobLeadStatusMachine.EVALUATING));
            lead.setUpdatedAt(Instant.now());
            leads.save(lead);
        }
        String profileHash = skillProfiles.profileHash(ownerId);
        String bucket = force ? String.valueOf(Instant.now().getEpochSecond() / 30) : "normal";
        String idempotency = JobRadarHashing.sha256(ownerId + "|" + lead.getId() + "|" + lead.getJdHash()
                + "|" + profileHash + "|" + promptVersion + "|" + bucket);
        ObjectNode input = objectMapper.createObjectNode();
        input.put("previousStatus", previousStatus);
        input.put("force", force);
        return runService.enqueue(ownerId, JobAgentRunService.KIND_JOB_EVALUATE, "job_lead",
                lead.getId(), idempotency, input).run();
    }

    private void enforceDailyLimit(String ownerId) {
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        long used = runs.countByOwnerIdAndKindAndCreatedAtGreaterThanEqual(
                ownerId, JobAgentRunService.KIND_JOB_EVALUATE, start);
        if (used >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "今天已经评估 " + dailyLimit + " 个岗位，明天再来");
        }
    }

    private Optional<JobEvaluationEntity> latestEvaluation(String ownerId, UUID leadId) {
        return evaluations.findFirstByJobLeadIdAndOwnerIdOrderByCreatedAtDesc(leadId, ownerId);
    }

    private String latestRunStatus(String ownerId, UUID leadId) {
        return runs.findFirstByOwnerIdAndKindAndSubjectIdAndStatusInOrderByCreatedAtDesc(
                        ownerId, JobAgentRunService.KIND_JOB_EVALUATE, leadId,
                        Set.of(JobAgentRunService.STATUS_PENDING, JobAgentRunService.STATUS_RUNNING,
                                JobAgentRunService.STATUS_DEFERRED, JobAgentRunService.STATUS_SUCCEEDED,
                                JobAgentRunService.STATUS_FAILED))
                .map(JobAgentRunEntity::getStatus).orElse(null);
    }

    private JobLeadView toDetailView(JobLeadEntity lead) {
        JobEvaluationEntity evaluation = latestEvaluation(lead.getOwnerId(), lead.getId()).orElse(null);
        return new JobLeadView(lead.getId(), lead.getCompany(), lead.getRole(), lead.getCity(),
                lead.getSalaryMinK(), lead.getSalaryMaxK(), lead.getJdText(), lead.getStatus(),
                lead.getSourceUrl(), lead.getNote(), lead.getCreatedAt(), lead.getUpdatedAt(), lead.getArchivedAt(),
                evaluation == null ? null : toEvaluationView(evaluation),
                latestRunStatus(lead.getOwnerId(), lead.getId()));
    }

    private static JobEvaluationView toEvaluationView(JobEvaluationEntity evaluation) {
        return new JobEvaluationView(evaluation.getId(), evaluation.getScore(), evaluation.getConfidence(),
                evaluation.getBreakdownJson(), evaluation.getHitsJson(), evaluation.getGapsJson(),
                evaluation.getReason(), evaluation.getRecommendation(), evaluation.isDegraded(),
                evaluation.getModel(), evaluation.getCreatedAt());
    }

    private static void validateJd(String jdText) {
        int length = jdText == null ? 0 : jdText.length();
        if (length < 80 || length > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "请粘贴完整的岗位职责与要求（80–20000 字）");
        }
    }

    private static void validateSalary(Integer min, Integer max) {
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "薪资下限不能高于上限");
        }
    }

    private static String dedupKey(String company, String role, String jdHash) {
        return JobRadarHashing.sha256(JobRadarHashing.normalizeText(company).toLowerCase() + "\n"
                + JobRadarHashing.normalizeText(role).toLowerCase() + "\n" + jdHash);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
