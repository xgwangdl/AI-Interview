package com.guangge.Interview.jobradar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobEvaluationRepository extends JpaRepository<JobEvaluationEntity, UUID> {
    Optional<JobEvaluationEntity> findFirstByJobLeadIdAndOwnerIdOrderByCreatedAtDesc(UUID jobLeadId, String ownerId);

    Optional<JobEvaluationEntity> findFirstByJobLeadIdAndJdHashAndProfileHashAndPromptVersionOrderByCreatedAtDesc(
            UUID jobLeadId, String jdHash, String profileHash, String promptVersion);

    void deleteAllByJobLeadId(UUID jobLeadId);
}
