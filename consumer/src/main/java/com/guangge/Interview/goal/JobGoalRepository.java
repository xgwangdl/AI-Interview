package com.guangge.Interview.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobGoalRepository extends JpaRepository<JobGoalEntity, UUID> {

    List<JobGoalEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<JobGoalEntity> findByIdAndOwnerId(UUID id, String ownerId);

    /** 首页/今日计划优先选"最近要面试的未归档目标"；没有面试日期的排后面。 */
    @Query("""
            select g from JobGoalEntity g
            where g.ownerId = :ownerId and g.status in ('preparing', 'applied', 'interviewing')
            order by case when g.interviewAt is null then 1 else 0 end,
                     g.interviewAt asc, g.createdAt desc
            """)
    List<JobGoalEntity> findActiveForPlan(@Param("ownerId") String ownerId);
}
