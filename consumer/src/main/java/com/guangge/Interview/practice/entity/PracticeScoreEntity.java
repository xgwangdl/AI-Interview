package com.guangge.Interview.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 一道题的成绩快照，独立于场次保留（见 V9 迁移与 docs/PRACTICE-ARCHIVE-REQUIREMENTS.md）。
 *
 * 只存分数与维度，**不存转写、不存录像** —— 档案要长期留着，隐私面就得尽量小。
 */
@Entity
@Table(name = "practice_score_archive")
public class PracticeScoreEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "session_created_at", nullable = false)
    private Instant sessionCreatedAt;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "question_id", nullable = false, length = 64)
    private String questionId;

    /**
     * 题干本身。V10 起才有：场次过了保留期会被清理，错题本要"重练这道题"就必须自己留住题面。
     * 追问题目靠 {@code questionCategory} 区分，不进错题本。
     */
    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Column(name = "question_category", length = 100)
    private String questionCategory;

    @Column(name = "role", length = 160)
    private String role;

    @Column(name = "difficulty", length = 32)
    private String difficulty;

    /** 原场次的面试类型与面试官风格：重练时沿用，不用让用户再选一遍。 */
    @Column(name = "interview_type", length = 32)
    private String interviewType;

    @Column(name = "interviewer_style", length = 32)
    private String interviewerStyle;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "relevance")
    private BigDecimal relevance;

    @Column(name = "completeness")
    private BigDecimal completeness;

    @Column(name = "professionalism")
    private BigDecimal professionalism;

    @Column(name = "star_structure")
    private BigDecimal starStructure;

    @Column(name = "logic")
    private BigDecimal logic;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public Instant getSessionCreatedAt() { return sessionCreatedAt; }
    public void setSessionCreatedAt(Instant sessionCreatedAt) { this.sessionCreatedAt = sessionCreatedAt; }

    public int getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(int questionIndex) { this.questionIndex = questionIndex; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionCategory() { return questionCategory; }
    public void setQuestionCategory(String questionCategory) { this.questionCategory = questionCategory; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }

    public String getInterviewerStyle() { return interviewerStyle; }
    public void setInterviewerStyle(String interviewerStyle) { this.interviewerStyle = interviewerStyle; }

    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal overallScore) { this.overallScore = overallScore; }

    public BigDecimal getRelevance() { return relevance; }
    public void setRelevance(BigDecimal relevance) { this.relevance = relevance; }

    public BigDecimal getCompleteness() { return completeness; }
    public void setCompleteness(BigDecimal completeness) { this.completeness = completeness; }

    public BigDecimal getProfessionalism() { return professionalism; }
    public void setProfessionalism(BigDecimal professionalism) { this.professionalism = professionalism; }

    public BigDecimal getStarStructure() { return starStructure; }
    public void setStarStructure(BigDecimal starStructure) { this.starStructure = starStructure; }

    public BigDecimal getLogic() { return logic; }
    public void setLogic(BigDecimal logic) { this.logic = logic; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
