package com.guangge.Interview.progress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReportEntity, UUID> {
    Optional<WeeklyReportEntity> findFirstByOwnerIdAndWeekStart(String ownerId, LocalDate weekStart);
}
