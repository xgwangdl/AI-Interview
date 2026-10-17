package com.guangge.Interview.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortMemberRepository extends JpaRepository<CohortMemberEntity, CohortMemberEntity.Key> {
    List<CohortMemberEntity> findAllByOwnerIdOrderByJoinedAtDesc(String ownerId);
    List<CohortMemberEntity> findAllByCohortIdOrderByJoinedAtAsc(java.util.UUID cohortId);
    Optional<CohortMemberEntity> findByCohortIdAndOwnerId(java.util.UUID cohortId, String ownerId);
    long countByCohortId(java.util.UUID cohortId);
}
