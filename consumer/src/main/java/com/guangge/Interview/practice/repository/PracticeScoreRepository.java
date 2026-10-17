package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

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

    List<PracticeScoreEntity> findAllByOwnerIdAndEvaluatedAtBetween(String ownerId, Instant from, Instant to);

    boolean existsBySessionIdAndQuestionId(UUID sessionId, String questionId);

    Optional<PracticeScoreEntity> findFirstBySessionIdAndQuestionId(UUID sessionId, String questionId);

    long countByOwnerId(String ownerId);

    /**
     * 错题本的数据源：带题面的成绩，按最近一次评分倒序。
     *
     * 不带分页是刻意的：错题本要按题目分组再排序，先截断行数会把某道题的重练历史切掉一半。
     */
    @Query("""
            select s from PracticeScoreEntity s
            where s.ownerId = :ownerId and s.questionText is not null
            order by s.evaluatedAt desc
            """)
    List<PracticeScoreEntity> findScoredQuestions(@Param("ownerId") String ownerId);

    /** 重练某道题时取最近一次记录：题面与当时的面试设置都从它身上复用。 */
    Optional<PracticeScoreEntity> findFirstByOwnerIdAndQuestionIdAndQuestionTextIsNotNullOrderByEvaluatedAtDesc(
            String ownerId, String questionId);

    void deleteAllBySessionId(UUID sessionId);

    void deleteAllByOwnerId(String ownerId);
}
