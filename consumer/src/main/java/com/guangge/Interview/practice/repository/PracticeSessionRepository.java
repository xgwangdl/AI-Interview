package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSessionEntity, UUID> {
    List<PracticeSessionEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);
    List<PracticeSessionEntity> findAllByOwnerIdAndCreatedAtBetween(String ownerId, Instant from, Instant to);
    List<PracticeSessionEntity> findAllByOwnerIdInAndStatusOrderByCreatedAtDesc(
            java.util.Collection<String> ownerIds, String status);
    List<PracticeSessionEntity> findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(String ownerId, java.util.UUID goalId);
    Optional<PracticeSessionEntity> findByIdAndOwnerId(UUID id, String ownerId);
    List<PracticeSessionEntity> findAllByRetentionUntilBefore(Instant instant);

    @org.springframework.data.jpa.repository.Query("""
            select s.id from PracticeSessionEntity s
            where s.ownerId = :ownerId and s.goalId = :goalId
            """)
    List<UUID> findIdsByOwnerIdAndGoalId(@org.springframework.data.repository.query.Param("ownerId") String ownerId,
                                         @org.springframework.data.repository.query.Param("goalId") java.util.UUID goalId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update PracticeSessionEntity s set s.goalId = null where s.goalId = :goalId")
    void detachGoal(@org.springframework.data.repository.query.Param("goalId") java.util.UUID goalId);
}
