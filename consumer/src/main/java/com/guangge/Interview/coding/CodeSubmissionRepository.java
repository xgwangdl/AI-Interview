package com.guangge.Interview.coding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeSubmissionRepository extends JpaRepository<CodeSubmissionEntity, UUID> {

    List<CodeSubmissionEntity> findAllByQuestionIdOrderByCreatedAtDesc(UUID questionId);

    List<CodeSubmissionEntity> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<CodeSubmissionEntity> findFirstByQuestionIdAndOwnerIdOrderByCreatedAtDesc(UUID questionId, String ownerId);

    void deleteAllBySessionId(UUID sessionId);
}
