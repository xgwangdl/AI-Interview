package com.guangge.Interview.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeOptimizationRepository extends JpaRepository<ResumeOptimizationEntity, UUID> {

    List<ResumeOptimizationEntity> findAllByResumeIdOrderByCreatedAtDesc(UUID resumeId);

    Optional<ResumeOptimizationEntity> findByIdAndOwnerId(UUID id, String ownerId);
}
