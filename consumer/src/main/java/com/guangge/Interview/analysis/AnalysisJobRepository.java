package com.guangge.Interview.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJobEntity, UUID> {

    List<AnalysisJobEntity> findAllByStatusOrderByCreatedAtAsc(String status);

    List<AnalysisJobEntity> findAllByStatusAndUpdatedAtBefore(String status, Instant instant);

    long countByStatus(String status);

    List<AnalysisJobEntity> findAllBySessionId(UUID sessionId);

    Optional<AnalysisJobEntity> findByAnswerId(UUID answerId);

    void deleteAllBySessionId(UUID sessionId);

    /**
     * 领取「已到期」的待处理任务。失败后 next_attempt_at 会被指数退避推后，
     * 因此这里必须按时间过滤，否则退避等于没做。
     * next_attempt_at 为空的旧数据视为立即可处理。
     */
    @Query("select job from AnalysisJobEntity job "
            + "where job.status = :status and (job.nextAttemptAt is null or job.nextAttemptAt <= :now) "
            + "order by job.createdAt asc")
    List<AnalysisJobEntity> findDueJobs(@Param("status") String status, @Param("now") Instant now);

    /**
     * 条件更新即「认领」：只有把状态从 from 改到 to 成功的那一次调用返回 1，
     * 多实例并发时其余实例会拿到 0 从而跳过，避免同一道题被重复转写与评分。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AnalysisJobEntity job set job.status = :to, job.updatedAt = :now "
            + "where job.id = :id and job.status = :from")
    int transitionStatus(@Param("id") UUID id, @Param("from") String from, @Param("to") String to,
                         @Param("now") Instant now);
}
