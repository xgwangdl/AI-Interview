package com.guangge.Interview.jobradar;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WeakPointService {
    private static final Map<String, String> DIMENSION_BY_CATEGORY = Map.of(
            "project_depth", "completeness",
            "system_design", "professionalism",
            "algorithm", "professionalism",
            "behavioral", "starStructure",
            "domain", "relevance",
            "other", "logic");

    private final TrainingWeakPointRepository weakPoints;

    public WeakPointService(TrainingWeakPointRepository weakPoints) {
        this.weakPoints = weakPoints;
    }

    @Transactional
    public void replaceForDebrief(String ownerId, UUID debriefId, List<DebriefQuestionView> questions,
                                  String selfNotes) {
        weakPoints.deleteAllInBatch(weakPoints.findAllByDebriefId(debriefId));
        List<DebriefQuestionView> candidates = questions.stream()
                .filter(question -> question.hard() || (question.note() != null && !question.note().isBlank()))
                .sorted(Comparator.comparingInt(question -> question.hard() ? 0 : 1))
                .limit(5).toList();
        if (candidates.isEmpty() && selfNotes != null && !selfNotes.isBlank()) {
            create(ownerId, debriefId, selfNotes.strip(), "relevance", selfNotes, 3);
            return;
        }
        for (DebriefQuestionView question : candidates) {
            String topic = question.text().length() <= 80 ? question.text() : question.text().substring(0, 80);
            String evidence = question.note() == null || question.note().isBlank()
                    ? "用户标记为本次面试中较难的问题" : question.note();
            create(ownerId, debriefId, topic,
                    DIMENSION_BY_CATEGORY.getOrDefault(question.category(), "logic"), evidence,
                    question.hard() ? 5 : 3);
        }
    }

    @Transactional(readOnly = true)
    public List<WeakPointView> forDebrief(UUID debriefId) {
        return weakPoints.findAllByDebriefId(debriefId).stream().map(WeakPointService::toView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String ownerId, String source, String status, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(50, limit));
        String safeStatus = status == null || status.isBlank() ? "open" : status.trim();
        var pageable = org.springframework.data.domain.PageRequest.of(safeOffset / safeLimit, safeLimit);
        var page = source == null || source.isBlank()
                ? weakPoints.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, safeStatus, pageable)
                : weakPoints.findAllByOwnerIdAndSourceAndStatusOrderByCreatedAtDesc(ownerId, source.trim(), safeStatus, pageable);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", page.getContent().stream().map(WeakPointService::toView).toList());
        result.put("total", page.getTotalElements());
        result.put("offset", safeOffset);
        result.put("limit", safeLimit);
        return result;
    }

    @Transactional
    public WeakPointView close(String ownerId, UUID id) {
        TrainingWeakPointEntity weakPoint = weakPoints.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短板不存在"));
        if (!"closed".equals(weakPoint.getStatus())) {
            weakPoint.setStatus("closed");
            weakPoint.setClosedAt(Instant.now());
            weakPoint.setClosedBy("manual");
            weakPoints.save(weakPoint);
        }
        return toView(weakPoint);
    }

    /** Auto-close only when a later practice has reached the existing 75-point mastery threshold. */
    @Scheduled(cron = "${practice.job-radar.reconcile-cron:0 17 */6 * * *}")
    @Transactional
    public void reconcile() {
        for (TrainingWeakPointEntity weakPoint : weakPoints.findTop500ByStatusOrderByCreatedAtAsc("open")) {
            if (weakPoints.existsLaterPassingPractice(weakPoint.getOwnerId(), weakPoint.getDimensionKey(),
                    weakPoint.getCreatedAt())) {
                weakPoint.setStatus("closed");
                weakPoint.setClosedAt(Instant.now());
                weakPoint.setClosedBy("auto");
                weakPoints.save(weakPoint);
            }
        }
    }

    private void create(String ownerId, UUID debriefId, String topic, String dimension,
                        String evidence, int weight) {
        TrainingWeakPointEntity entity = new TrainingWeakPointEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setSource("real_interview");
        entity.setDebriefId(debriefId);
        entity.setTopic(topic);
        entity.setDimensionKey(dimension);
        entity.setEvidence(evidence == null ? null : evidence.length() <= 1000 ? evidence : evidence.substring(0, 1000));
        entity.setWeight(Math.max(1, Math.min(5, weight)));
        entity.setStatus("open");
        entity.setCreatedAt(Instant.now());
        weakPoints.save(entity);
    }

    private static WeakPointView toView(TrainingWeakPointEntity entity) {
        return new WeakPointView(entity.getId(), entity.getSource(), entity.getTopic(), entity.getDimensionKey(),
                entity.getEvidence(), entity.getWeight(), entity.getStatus(), entity.getCreatedAt(),
                entity.getClosedAt(), entity.getClosedBy());
    }
}
