package com.guangge.Interview.language;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageTurnRepository extends JpaRepository<LanguageTurnEntity, UUID> {

    List<LanguageTurnEntity> findAllBySessionIdOrderByTurnIndexAsc(UUID sessionId);

    Optional<LanguageTurnEntity> findBySessionIdAndTurnIndex(UUID sessionId, int turnIndex);
}
