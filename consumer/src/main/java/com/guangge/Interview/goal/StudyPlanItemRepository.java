package com.guangge.Interview.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPlanItemRepository extends JpaRepository<StudyPlanItemEntity, UUID> {

    List<StudyPlanItemEntity> findAllByPlanIdAndPlanDateOrderByPositionAsc(UUID planId, LocalDate planDate);

    List<StudyPlanItemEntity> findAllByPlanIdOrderByPlanDateAscPositionAsc(UUID planId);

    List<StudyPlanItemEntity> findAllByPlanIdIn(List<UUID> planIds);

    List<StudyPlanItemEntity> findAllByOwnerIdAndPlanDateBetweenOrderByPlanDateDescPositionAsc(
            String ownerId, LocalDate from, LocalDate to);

    Optional<StudyPlanItemEntity> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<StudyPlanItemEntity> findFirstBySessionId(UUID sessionId);

    long countByOwnerIdAndPlanDateAndStatus(String ownerId, LocalDate planDate, String status);
}
