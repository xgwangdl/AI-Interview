package com.guangge.Interview.analysis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalysisDlqRepository extends JpaRepository<AnalysisDlqEntity, UUID> {

    List<AnalysisDlqEntity> findAllBySessionIdInOrderByMovedAtDesc(Collection<UUID> sessionIds, Pageable pageable);

    List<AnalysisDlqEntity> findAllByOrderByMovedAtDesc(Pageable pageable);

    long countBySessionIdIn(Collection<UUID> sessionIds);
}
