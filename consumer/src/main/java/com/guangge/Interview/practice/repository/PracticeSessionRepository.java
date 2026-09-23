package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSessionEntity, UUID> {
    List<PracticeSessionEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);
    Optional<PracticeSessionEntity> findByIdAndOwnerId(UUID id, String ownerId);
    List<PracticeSessionEntity> findAllByRetentionUntilBefore(Instant instant);
}
