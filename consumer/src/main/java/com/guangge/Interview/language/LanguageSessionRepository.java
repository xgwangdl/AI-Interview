package com.guangge.Interview.language;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageSessionRepository extends JpaRepository<LanguageSessionEntity, UUID> {

    List<LanguageSessionEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<LanguageSessionEntity> findByIdAndOwnerId(UUID id, String ownerId);

    /** 同一账号只允许一场"进行中"：新开一场时把旧的标成 abandoned（见 Service）。 */
    List<LanguageSessionEntity> findAllByOwnerIdAndStatus(String ownerId, String status);

    List<LanguageSessionEntity> findAllByRetentionUntilBefore(Instant instant);
}
