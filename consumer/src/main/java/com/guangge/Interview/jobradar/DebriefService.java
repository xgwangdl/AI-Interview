package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DebriefService {
    private final InterviewDebriefRepository debriefs;
    private final JobLeadService leads;
    private final PracticeSessionRepository practiceSessions;
    private final JobAgentRunService runs;
    private final WeakPointService weakPoints;
    private final ObjectMapper objectMapper;

    public DebriefService(InterviewDebriefRepository debriefs,
                          JobLeadService leads,
                          PracticeSessionRepository practiceSessions,
                          JobAgentRunService runs,
                          WeakPointService weakPoints,
                          ObjectMapper objectMapper) {
        this.debriefs = debriefs;
        this.leads = leads;
        this.practiceSessions = practiceSessions;
        this.runs = runs;
        this.weakPoints = weakPoints;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DebriefCreateResponse create(String ownerId, DebriefCreateRequest request) {
        if (!request.privacyAcknowledged()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "请先确认隐私提示：回填内容会发送给模型用于复盘，并可随时彻底删除");
        }
        if (request.jobLeadId() != null) leads.requireOwned(ownerId, request.jobLeadId());
        if (request.practiceSessionId() != null && practiceSessions
                .findByIdAndOwnerId(request.practiceSessionId(), ownerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "练习记录不存在");
        }
        String rawText = request.rawText().strip();
        Instant now = Instant.now();
        InterviewDebriefEntity debrief = new InterviewDebriefEntity();
        debrief.setId(UUID.randomUUID());
        debrief.setOwnerId(ownerId);
        debrief.setJobLeadId(request.jobLeadId());
        debrief.setPracticeSessionId(request.practiceSessionId());
        debrief.setInterviewDate(request.interviewDate());
        debrief.setRoundName(request.roundName().trim());
        debrief.setOutcome(request.outcome() == null || request.outcome().isBlank() ? "unknown" : request.outcome());
        debrief.setRawText(rawText);
        debrief.setRawTextSha256(JobRadarHashing.sha256(rawText));
        debrief.setRawTextLength(rawText.length());
        debrief.setPrivacyAcknowledgedAt(now);
        debrief.setInterviewerFocus(trimToNull(request.interviewerFocus()));
        debrief.setSelfNotes(trimToNull(request.selfNotes()));
        debrief.setParseStatus("pending");
        debrief.setCreatedAt(now);
        debrief.setUpdatedAt(now);
        debriefs.save(debrief);

        ObjectNode input = objectMapper.createObjectNode();
        input.put("debriefId", debrief.getId().toString());
        input.put("rawTextLength", rawText.length());
        JobAgentRunEntity run = runs.enqueue(ownerId, JobAgentRunService.KIND_DEBRIEF_PARSE,
                "interview_debrief", debrief.getId(),
                JobRadarHashing.sha256(ownerId + "|debrief|" + debrief.getId()), input).run();
        return new DebriefCreateResponse(debrief.getId(), run.getId(), debrief.getParseStatus());
    }

    @Transactional(readOnly = true)
    public DebriefView get(String ownerId, UUID id) {
        InterviewDebriefEntity debrief = requireOwned(ownerId, id);
        return toView(debrief);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> list(String ownerId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(50, limit));
        var page = debriefs.findAllByOwnerIdOrderByInterviewDateDesc(ownerId,
                PageRequest.of(safeOffset / safeLimit, safeLimit));
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", page.getContent().stream().map(this::toView).toList());
        result.put("total", page.getTotalElements());
        result.put("offset", safeOffset);
        result.put("limit", safeLimit);
        return result;
    }

    @Transactional
    public DebriefView updateQuestions(String ownerId, UUID id, DebriefQuestionUpdateRequest request) {
        InterviewDebriefEntity debrief = requireOwned(ownerId, id);
        List<DebriefQuestionView> questions = new ArrayList<>();
        if (request.questions() != null) {
            for (int i = 0; i < request.questions().size(); i++) {
                var question = request.questions().get(i);
                questions.add(new DebriefQuestionView(i + 1, question.text().strip(), question.category(),
                        question.hard(), trimToNull(question.note())));
            }
        }
        debrief.setQuestionsJson(toQuestionsJson(questions));
        debrief.setParseStatus("manual");
        debrief.setUpdatedAt(Instant.now());
        debriefs.save(debrief);
        weakPoints.replaceForDebrief(ownerId, debrief.getId(), questions, debrief.getSelfNotes());
        return toView(debrief);
    }

    @Transactional
    public void delete(String ownerId, UUID id) {
        InterviewDebriefEntity debrief = requireOwned(ownerId, id);
        runs.cancelForDeletedSubject(id);
        // Hard delete: raw_text, questions and dependent weak points are removed by the database.
        debriefs.delete(debrief);
    }

    @Transactional(readOnly = true)
    public InterviewDebriefEntity requireOwned(String ownerId, UUID id) {
        return debriefs.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "面试回填不存在"));
    }

    @Transactional
    public void applyParsedQuestions(InterviewDebriefEntity debrief,
                                     List<DebriefParseResult.DebriefQuestion> parsed,
                                     List<String> interviewerFocus, String parseStatus) {
        List<DebriefQuestionView> questions = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            DebriefParseResult.DebriefQuestion question = parsed.get(i);
            questions.add(new DebriefQuestionView(i + 1, question.text(), question.category(),
                    question.hard(), trimToNull(question.note())));
        }
        debrief.setQuestionsJson(toQuestionsJson(questions));
        debrief.setParseStatus(parseStatus);
        if (interviewerFocus != null && !interviewerFocus.isEmpty()) {
            debrief.setInterviewerFocus(String.join(",", interviewerFocus).length() <= 200
                    ? String.join(",", interviewerFocus) : debrief.getInterviewerFocus());
        }
        debrief.setUpdatedAt(Instant.now());
        debriefs.save(debrief);
        weakPoints.replaceForDebrief(debrief.getOwnerId(), debrief.getId(), questions, debrief.getSelfNotes());
    }

    private DebriefView toView(InterviewDebriefEntity debrief) {
        return new DebriefView(debrief.getId(), debrief.getJobLeadId(), debrief.getPracticeSessionId(),
                debrief.getInterviewDate(), debrief.getRoundName(), debrief.getOutcome(), debrief.getParseStatus(),
                parseQuestions(debrief.getQuestionsJson()), parseFocus(debrief.getInterviewerFocus()),
                weakPoints.forDebrief(debrief.getId()), debrief.getRawTextLength(),
                debrief.getPrivacyAcknowledgedAt(), debrief.getCreatedAt(), debrief.getUpdatedAt());
    }

    private ArrayNode toQuestionsJson(List<DebriefQuestionView> questions) {
        ArrayNode array = objectMapper.createArrayNode();
        for (DebriefQuestionView question : questions) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("index", question.index());
            node.put("text", question.text());
            node.put("category", question.category());
            node.put("hard", question.hard());
            if (question.note() == null) node.putNull("note"); else node.put("note", question.note());
            array.add(node);
        }
        return array;
    }

    private static List<DebriefQuestionView> parseQuestions(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<DebriefQuestionView> result = new ArrayList<>();
        int fallbackIndex = 1;
        for (JsonNode item : node) {
            String text = item.path("text").asText("").strip();
            if (text.isBlank()) continue;
            result.add(new DebriefQuestionView(item.path("index").asInt(fallbackIndex++), text,
                    item.path("category").asText("other"), item.path("hard").asBoolean(false),
                    item.path("note").isNull() ? null : item.path("note").asText(null)));
        }
        return List.copyOf(result);
    }

    private static List<String> parseFocus(String focus) {
        if (focus == null || focus.isBlank()) return List.of();
        return Arrays.stream(focus.split(",")).map(String::strip).filter(value -> !value.isBlank()).toList();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
