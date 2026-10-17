package com.guangge.Interview.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CohortRepository extends JpaRepository<CohortEntity, UUID> {
    Optional<CohortEntity> findByInviteCodeIgnoreCase(String inviteCode);
}
