package com.guangge.Interview.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecordingConsentRepository extends JpaRepository<RecordingConsentEntity, UUID> {
    Optional<RecordingConsentEntity> findFirstByOwnerIdAndConsentTypeAndVersionOrderByAcceptedAtDesc(
            String ownerId, String consentType, String version);

    boolean existsByOwnerIdAndConsentTypeAndVersion(String ownerId, String consentType, String version);
}
