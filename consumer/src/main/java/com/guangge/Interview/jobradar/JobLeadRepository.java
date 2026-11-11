package com.guangge.Interview.jobradar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobLeadRepository extends JpaRepository<JobLeadEntity, UUID> {
    Optional<JobLeadEntity> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<JobLeadEntity> findByOwnerIdAndDedupKey(String ownerId, String dedupKey);

    @Query("""
            select lead from JobLeadEntity lead
            where lead.ownerId = :ownerId
              and (:status is null or lead.status = :status)
              and (:role is null or lower(lead.role) like lower(concat('%', cast(:role as string), '%')))
            order by lead.createdAt desc
            """)
    Page<JobLeadEntity> search(@Param("ownerId") String ownerId,
                               @Param("status") String status,
                               @Param("role") String role,
                               Pageable pageable);

    @Query("select lead.status, count(lead) from JobLeadEntity lead where lead.ownerId = :ownerId group by lead.status")
    List<Object[]> countByStatus(@Param("ownerId") String ownerId);

    List<JobLeadEntity> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    long countByOwnerIdAndStatusIn(String ownerId, List<String> statuses);
}
