package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PracticeScoreRepository extends JpaRepository<PracticeScoreEntity, UUID> {

    /** 最近参与过练习（且有成绩）的场次，按场次创建时间倒序 —— 训练档案的窗口。 */
    @Query("""
            select s.sessionId from PracticeScoreEntity s
            where s.ownerId = :ownerId
            group by s.sessionId
            order by max(s.sessionCreatedAt) desc
            """)
    List<UUID> findRecentSessionIds(@Param("ownerId") String ownerId, Pageable pageable);

    List<PracticeScoreEntity> findAllByOwnerIdAndSessionIdIn(String ownerId, List<UUID> sessionIds);

    boolean existsBySessionIdAndQuestionId(UUID sessionId, String questionId);

    long countByOwnerId(String ownerId);

    void deleteAllBySessionId(UUID sessionId);

    void deleteAllByOwnerId(String ownerId);
}
