package com.guangge.Interview.coding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodingSessionRepository extends JpaRepository<CodingSessionEntity, UUID> {

    List<CodingSessionEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<CodingSessionEntity> findByIdAndOwnerId(UUID id, String ownerId);

    List<CodingSessionEntity> findAllByRetentionUntilBefore(Instant instant);
}
