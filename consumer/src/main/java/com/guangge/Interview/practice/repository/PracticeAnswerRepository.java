package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswerEntity, UUID> {
    List<PracticeAnswerEntity> findAllBySessionIdOrderByQuestionIndexAsc(UUID sessionId);
    List<PracticeAnswerEntity> findAllBySessionIdAndIsCurrentTrueOrderByQuestionIndexAsc(UUID sessionId);
    List<PracticeAnswerEntity> findAllBySessionIdOrderByQuestionIndexAscAttemptAsc(UUID sessionId);
    List<PracticeAnswerEntity> findAllBySessionIdAndQuestionIndexOrderByAttemptAsc(UUID sessionId, int questionIndex);
    long countBySessionIdIn(java.util.Collection<UUID> sessionIds);
    long countBySessionIdInAndIsCurrentTrue(java.util.Collection<UUID> sessionIds);
    Optional<PracticeAnswerEntity> findFirstBySessionIdAndQuestionIndexAndIsCurrentTrueOrderByAttemptDesc(
            UUID sessionId, int questionIndex);
    long countBySessionIdAndQuestionIndex(UUID sessionId, int questionIndex);
    Optional<PracticeAnswerEntity> findByIdAndSessionId(UUID id, UUID sessionId);
    void deleteAllBySessionId(UUID sessionId);
}
