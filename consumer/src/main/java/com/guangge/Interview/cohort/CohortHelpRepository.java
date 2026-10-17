package com.guangge.Interview.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CohortHelpRepository extends JpaRepository<CohortHelpEntity, UUID> {
    List<CohortHelpEntity> findAllByCohortIdOrderByCreatedAtDesc(UUID cohortId);
    List<CohortHelpEntity> findAllByCohortIdAndStatusOrderByCreatedAtDesc(UUID cohortId, String status);
    List<CohortHelpEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
