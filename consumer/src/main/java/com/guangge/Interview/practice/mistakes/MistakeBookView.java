package com.guangge.Interview.practice.mistakes;

import java.time.Instant;
import java.util.List;

/**
 * 错题本：把"没答好的具体那道题"挑出来，可以就地重练。
 *
 * 与训练档案的分工：档案看的是"哪个维度弱"（跨场次的维度平均），
 * 错题本看的是"哪句话没答出来"（同一道题反复练到过线为止）。
 */
public record MistakeBookView(
        String scopeNote,
        double passThreshold,
        /** 当前筛选条件下的错题总数：分页算总数、入口写"查看全部 N 道"都用它。 */
        int total,
        int offset,
        int limit,
        /** 当前筛选的岗位（空 = 全部）。 */
        String role,
        /** 有错题的岗位与数量，给筛选下拉用（永远基于**未筛选**的错题，否则选完就只剩一个选项）。 */
        List<RoleCount> roles,
        List<Mistake> mistakes) {

    public record RoleCount(String role, int count) { }

    /**
     * 一道错题。
     *
     * {@code attempts} / {@code bestScore} 让"重练有没有用"看得见：
     * 练过一次并过线后，这道题就会从列表里消失。
     */
    public record Mistake(
            String questionId,
            String text,
            String category,
            String role,
            String difficulty,
            String interviewType,
            String interviewerStyle,
            int attempts,
            Double bestScore,
            Double latestScore,
            Instant latestAt,
            String weakestDimension,
            Double weakestScore) { }
}
