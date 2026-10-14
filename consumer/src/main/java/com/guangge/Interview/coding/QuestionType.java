package com.guangge.Interview.coding;

import java.util.Arrays;
import java.util.Optional;

/** 笔试支持的题型。 */
public enum QuestionType {

    /** 上机编程：写完整程序，跑测试用例判分。 */
    CODING("coding"),

    /** 数据库题：写 SQL，在临时 SQLite 上执行并比对结果集。 */
    SQL("sql"),

    /** 编程填空：starter_code 里留占位符，按空判分。 */
    FILL_BLANK("fill_blank"),

    /** 选择题：比对选项。 */
    CHOICE("choice"),

    /** 简答题：没有唯一答案，交给模型按要点评分。 */
    SHORT_ANSWER("short_answer");

    private final String id;

    QuestionType(String id) {
        this.id = id;
    }

    public String id() { return id; }

    /** 需要沙箱执行的题型。 */
    public boolean needsExecution() {
        return this == CODING || this == SQL;
    }

    public static Optional<QuestionType> fromId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.trim().toLowerCase();
        return Arrays.stream(values()).filter(item -> item.id.equals(normalized)).findFirst();
    }
}
