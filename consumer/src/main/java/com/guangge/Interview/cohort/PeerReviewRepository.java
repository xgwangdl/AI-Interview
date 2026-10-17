package com.guangge.Interview.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PeerReviewRepository extends JpaRepository<PeerReviewEntity, UUID> {
    boolean existsByCohortIdAndReviewerIdAndAnswerId(UUID cohortId, String reviewerId, UUID answerId);
    List<PeerReviewEntity> findAllByCohortIdAndTargetOwnerIdOrderByCreatedAtDesc(UUID cohortId, String targetOwnerId);
    long countByCohortIdAndTargetOwnerId(UUID cohortId, String targetOwnerId);
}
