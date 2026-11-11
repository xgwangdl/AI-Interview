package com.guangge.Interview.jobradar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingWeakPointRepository extends JpaRepository<TrainingWeakPointEntity, UUID> {
    Optional<TrainingWeakPointEntity> findByIdAndOwnerId(UUID id, String ownerId);

    Page<TrainingWeakPointEntity> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(
            String ownerId, String status, Pageable pageable);

    Page<TrainingWeakPointEntity> findAllByOwnerIdAndSourceAndStatusOrderByCreatedAtDesc(
            String ownerId, String source, String status, Pageable pageable);

    List<TrainingWeakPointEntity> findAllByDebriefId(UUID debriefId);

    List<TrainingWeakPointEntity> findAllByOwnerIdAndStatus(String ownerId, String status);

    List<TrainingWeakPointEntity> findTop500ByStatusOrderByCreatedAtAsc(String status);

    Optional<TrainingWeakPointEntity> findFirstByOwnerIdAndStatusOrderByCreatedAtAsc(String ownerId, String status);

    long countByOwnerIdAndStatusAndCreatedAtBetween(
            String ownerId, String status, java.time.Instant from, java.time.Instant to);

    long countByOwnerIdAndStatusAndClosedAtBetween(
            String ownerId, String status, java.time.Instant from, java.time.Instant to);

    long countByOwnerIdAndCreatedAtBetween(String ownerId, java.time.Instant from, java.time.Instant to);

    /**
     * A weak point closes only when a later practice has a passing score in the
     * same dimension. 75 is intentionally the same threshold as the mistake book.
     */
    @Query(value = """
            select exists (
                select 1 from practice_score_archive score
                where score.owner_id = :ownerId
                  and score.session_created_at > :after
                  and case :dimension
                      when 'relevance' then score.relevance
                      when 'completeness' then score.completeness
                      when 'professionalism' then score.professionalism
                      when 'starStructure' then score.star_structure
                      when 'logic' then score.logic
                      else null
                  end >= 75
            )
            """, nativeQuery = true)
    boolean existsLaterPassingPractice(@Param("ownerId") String ownerId,
                                       @Param("dimension") String dimension,
                                       @Param("after") java.time.Instant after);
}
