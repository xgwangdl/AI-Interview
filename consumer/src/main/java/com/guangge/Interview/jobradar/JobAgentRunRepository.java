package com.guangge.Interview.jobradar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAgentRunRepository extends JpaRepository<JobAgentRunEntity, UUID> {
    Optional<JobAgentRunEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<JobAgentRunEntity> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<JobAgentRunEntity> findFirstByOwnerIdAndKindAndSubjectIdAndStatusInOrderByCreatedAtDesc(
            String ownerId, String kind, UUID subjectId, Collection<String> statuses);

    List<JobAgentRunEntity> findTop20ByOwnerIdOrderByCreatedAtDesc(String ownerId);

    long countByOwnerIdAndKindAndCreatedAtGreaterThanEqual(String ownerId, String kind, Instant since);

    List<JobAgentRunEntity> findAllByOwnerIdAndKindAndCreatedAtBetween(
            String ownerId, String kind, Instant from, Instant to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobAgentRunEntity run
               set run.status = 'pending', run.leaseExpiresAt = null,
                   run.nextAttemptAt = :now, run.updatedAt = :now,
                   run.error = '任务租约已过期，自动重新领取'
             where run.status = 'running' and run.leaseExpiresAt < :now
            """)
    int reclaimExpiredLeases(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobAgentRunEntity run
               set run.status = 'cancelled', run.finishedAt = :now, run.updatedAt = :now,
                   run.inputJson = null, run.error = '主体已删除，任务取消并清除输入'
             where run.subjectId = :subjectId
               and run.status in ('pending', 'running', 'deferred')
            """)
    int cancelForDeletedSubject(@Param("subjectId") UUID subjectId, @Param("now") Instant now);

    void deleteAllByStatusInAndFinishedAtBefore(Collection<String> statuses, Instant cutoff);
}
