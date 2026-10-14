package com.guangge.Interview.coding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodeQuestionRepository extends JpaRepository<CodeQuestionEntity, UUID> {

    List<CodeQuestionEntity> findAllBySessionIdOrderByPositionAsc(UUID sessionId);

    void deleteAllBySessionId(UUID sessionId);
}
