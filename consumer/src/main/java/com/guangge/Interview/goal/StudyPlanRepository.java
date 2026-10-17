package com.guangge.Interview.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPlanRepository extends JpaRepository<StudyPlanEntity, UUID> {

    List<StudyPlanEntity> findAllByOwnerIdAndStatus(String ownerId, String status);

    Optional<StudyPlanEntity> findByIdAndOwnerId(UUID id, String ownerId);

    List<StudyPlanEntity> findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(String ownerId, UUID goalId);

    /** 目标被删除时连计划一起删；练习记录保留，只是不再挂目标。 */
    void deleteAllByGoalId(UUID goalId);
}
