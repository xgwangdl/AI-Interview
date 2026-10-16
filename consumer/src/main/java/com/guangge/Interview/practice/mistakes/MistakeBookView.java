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
        int total,
        List<Mistake> mistakes) {

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
