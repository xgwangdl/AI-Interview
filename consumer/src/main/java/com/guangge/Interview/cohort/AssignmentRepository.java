package com.guangge.Interview.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {
    List<AssignmentEntity> findAllByCohortIdOrderByCreatedAtDesc(UUID cohortId);
    List<AssignmentEntity> findAllByDueAtBetween(Instant from, Instant to);
}
