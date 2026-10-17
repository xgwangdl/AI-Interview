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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PeerReviewService {

    private final CohortService cohorts;
    private final CohortMemberRepository members;
    private final PeerReviewRepository reviews;
    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final PracticeQuestionCatalog questions;

    public PeerReviewService(CohortService cohorts, CohortMemberRepository members, PeerReviewRepository reviews,
                             PracticeSessionRepository sessions, PracticeAnswerRepository answers,
                             PracticeQuestionCatalog questions) {
        this.cohorts = cohorts;
        this.members = members;
        this.reviews = reviews;
        this.sessions = sessions;
        this.answers = answers;
        this.questions = questions;
    }

    @Transactional
    public void optIn(String ownerId, UUID cohortId, boolean enabled) {
        CohortMemberEntity member = cohorts.requireMember(cohortId, ownerId);
        member.setPeerReviewEnabled(enabled);
        members.save(member);
    }

    /**
     * 待互评任务：只看同伴的转写，不看录像；只展示已开启互评的成员。
     * 已评过的 answer 不再出现，避免重复评价。
     */
    @Transactional(readOnly = true)
    public List<PeerReviewTaskView> tasks(String ownerId, UUID cohortId, int limit) {
        CohortMemberEntity me = cohorts.requireMember(cohortId, ownerId);
        if (!me.isPeerReviewEnabled()) return List.of();
        List<String> peers = members.findAllByCohortIdOrderByJoinedAtAsc(cohortId).stream()
                .filter(member -> !member.getOwnerId().equals(ownerId))
                .filter(CohortMemberEntity::isPeerReviewEnabled)
                .map(CohortMemberEntity::getOwnerId)
                .toList();
        if (peers.isEmpty()) return List.of();

        List<PeerReviewTaskView> tasks = new ArrayList<>();
        for (PracticeSessionEntity session : sessions.findAllByOwnerIdInAndStatusOrderByCreatedAtDesc(peers, "completed")) {
            for (PracticeAnswerEntity answer : answers.findAllBySessionIdAndIsCurrentTrueOrderByQuestionIndexAsc(
                    session.getId())) {
                if (tasks.size() >= Math.max(1, Math.min(limit, 20))) return tasks;
                if (answer.getTranscriptText() == null || answer.getTranscriptText().isBlank()) continue;
                if (reviews.existsByCohortIdAndReviewerIdAndAnswerId(cohortId, ownerId, answer.getId())) continue;
                tasks.add(new PeerReviewTaskView(answer.getId(),
                        questions.questionText(session.getQuestionsJson(), answer.getQuestionId()),
                        answer.getTranscriptText(), alias(session.getOwnerId()), answer.getCreatedAt()));
            }
        }
        return tasks;
    }

    @Transactional
    public void submit(String reviewerId, UUID cohortId, PeerReviewRequest request) {
        CohortMemberEntity reviewer = cohorts.requireMember(cohortId, reviewerId);
        if (!reviewer.isPeerReviewEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先开启同伴互评");
        }
        PracticeAnswerEntity answer = answers.findById(request.answerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在"));
        PracticeSessionEntity session = sessions.findById(answer.getSessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "练习不存在"));
        String target = session.getOwnerId();
        if (target.equals(reviewerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能评价自己的回答");
        }
        CohortMemberEntity targetMember = members.findByCohortIdAndOwnerId(cohortId, target)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "同伴不在这个小组"));
        if (!targetMember.isPeerReviewEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "对方没有开启同伴互评");
        }
        if (!answer.isCurrent() || answer.getTranscriptText() == null || answer.getTranscriptText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这道题还没有可评价的转写");
        }
        if (reviews.existsByCohortIdAndReviewerIdAndAnswerId(cohortId, reviewerId, request.answerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已经评价过这道题");
        }
        PeerReviewEntity entity = new PeerReviewEntity();
        entity.setId(UUID.randomUUID());
        entity.setCohortId(cohortId);
        entity.setReviewerId(reviewerId);
        entity.setTargetOwnerId(target);
        entity.setAnswerId(request.answerId());
        entity.setClarity(request.clarity());
        entity.setRelevance(request.relevance());
        entity.setDepth(request.depth());
        entity.setSuggestion(request.suggestion().strip());
        entity.setCreatedAt(Instant.now());
        reviews.save(entity);
    }

    @Transactional(readOnly = true)
    public List<PeerReviewReceivedView> received(String ownerId, UUID cohortId) {
        cohorts.requireMember(cohortId, ownerId);
        return reviews.findAllByCohortIdAndTargetOwnerIdOrderByCreatedAtDesc(cohortId, ownerId).stream()
                .map(review -> new PeerReviewReceivedView(review.getId(), alias(review.getReviewerId()),
                        review.getClarity(), review.getRelevance(), review.getDepth(),
                        review.getSuggestion(), review.getCreatedAt()))
                .toList();
    }

    private static String alias(String ownerId) {
        return "同伴 " + String.format("%04d", Math.abs(ownerId.hashCode()) % 10000);
    }
}
