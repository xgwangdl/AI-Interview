package com.guangge.Interview.cohort;

import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CohortHelpService {

    private final CohortService cohorts;
    private final CohortHelpRepository help;
    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final PracticeQuestionCatalog questions;

    public CohortHelpService(CohortService cohorts, CohortHelpRepository help, PracticeSessionRepository sessions,
                             PracticeAnswerRepository answers, PracticeQuestionCatalog questions) {
        this.cohorts = cohorts;
        this.help = help;
        this.sessions = sessions;
        this.answers = answers;
        this.questions = questions;
    }

    @Transactional
    public CohortHelpView create(String ownerId, UUID cohortId, CohortHelpRequest request) {
        cohorts.requireMember(cohortId, ownerId);
        PracticeSessionEntity session = sessions.findByIdAndOwnerId(request.sessionId(), ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "练习不存在"));
        PracticeAnswerEntity answer = answers.findByIdAndSessionId(request.answerId(), request.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在"));
        if (answer.getQuestionIndex() != request.questionIndex()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "题号与回答不一致");
        }
        CohortHelpEntity entity = new CohortHelpEntity();
        entity.setId(UUID.randomUUID());
        entity.setCohortId(cohortId);
        entity.setOwnerId(ownerId);
        entity.setSessionId(request.sessionId());
        entity.setQuestionIndex(request.questionIndex());
        entity.setAnswerId(request.answerId());
        entity.setNote(request.note() == null ? null : request.note().strip());
        entity.setStatus("open");
        entity.setCreatedAt(Instant.now());
        return view(help.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CohortHelpView> list(String ownerId, UUID cohortId, String status) {
        CohortMemberEntity member = cohorts.requireMember(cohortId, ownerId);
        List<CohortHelpEntity> rows;
        if ("coach".equals(member.getRole())) {
            rows = status == null || status.isBlank()
                    ? help.findAllByCohortIdOrderByCreatedAtDesc(cohortId)
                    : help.findAllByCohortIdAndStatusOrderByCreatedAtDesc(cohortId, status);
        } else {
            rows = help.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                    .filter(item -> item.getCohortId().equals(cohortId))
                    .filter(item -> status == null || status.isBlank() || status.equals(item.getStatus()))
                    .toList();
        }
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public CohortHelpView feedback(String coachId, UUID cohortId, UUID helpId, String text) {
        cohorts.requireCoach(cohortId, coachId);
        CohortHelpEntity entity = help.findById(helpId)
                .filter(item -> item.getCohortId().equals(cohortId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "求助不存在"));
        entity.setFeedbackText(text.strip());
        entity.setCoachId(coachId);
        entity.setStatus("answered");
        entity.setAnsweredAt(Instant.now());
        return view(help.save(entity));
    }

    private CohortHelpView view(CohortHelpEntity entity) {
        PracticeSessionEntity session = sessions.findById(entity.getSessionId()).orElse(null);
        PracticeAnswerEntity answer = answers.findById(entity.getAnswerId()).orElse(null);
        String question = session == null || answer == null ? ""
                : questions.questionText(session.getQuestionsJson(), answer.getQuestionId());
        return new CohortHelpView(entity.getId(), entity.getOwnerId(), question,
                answer == null ? "" : answer.getTranscriptText(), entity.getNote(), entity.getStatus(),
                entity.getFeedbackText(), entity.getCreatedAt(), entity.getAnsweredAt());
    }
}
