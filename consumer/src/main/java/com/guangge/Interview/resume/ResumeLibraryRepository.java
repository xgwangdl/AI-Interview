package com.guangge.Interview.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeLibraryRepository extends JpaRepository<ResumeEntity, UUID> {

    List<ResumeEntity> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    Optional<ResumeEntity> findByIdAndOwnerId(UUID id, String ownerId);
}
