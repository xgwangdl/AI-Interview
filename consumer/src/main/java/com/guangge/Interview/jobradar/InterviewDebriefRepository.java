package com.guangge.Interview.jobradar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterviewDebriefRepository extends JpaRepository<InterviewDebriefEntity, UUID> {
    Optional<InterviewDebriefEntity> findByIdAndOwnerId(UUID id, String ownerId);

    Page<InterviewDebriefEntity> findAllByOwnerIdOrderByInterviewDateDesc(String ownerId, Pageable pageable);
}
