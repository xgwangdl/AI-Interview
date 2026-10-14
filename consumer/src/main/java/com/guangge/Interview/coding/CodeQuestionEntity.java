package com.guangge.Interview.coding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 一道笔试题；五种题型共用，各题型只填自己用到的列（详见 V6 迁移里的注释）。 */
@Entity
@Table(name = "code_questions")
@Getter
@Setter
public class CodeQuestionEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 32)
    private String language;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String statement;

    @Column(name = "starter_code", columnDefinition = "text")
    private String starterCode;

    @Column(name = "blanks_json", columnDefinition = "text")
    private String blanksJson;

    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    /** 参考答案：上机/SQL 是参考解，填空/选择是正确项，简答是参考要点。 */
    @Column(name = "answer_json", columnDefinition = "text")
    private String answerJson;

    @Column(name = "test_cases_json", columnDefinition = "text")
    private String testCasesJson;

    @Column(name = "sql_setup", columnDefinition = "text")
    private String sqlSetup;

    @Column(length = 32)
    private String difficulty;

    @Column(length = 255)
    private String tags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
