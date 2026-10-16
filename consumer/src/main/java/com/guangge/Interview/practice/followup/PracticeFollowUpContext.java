package com.guangge.Interview.practice.followup;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次"要不要追问"的判断需要的全部上下文。
 *
 * 不只是当前的回答：连续追问要看得到前几轮问过什么、答了什么，
 * 否则第二次追问只会换个说法重复第一句，越问越水。
 */
public record PracticeFollowUpContext(
        String role,
        String jobDescription,
        String rootQuestion,
        List<Turn> turns,
        int round) {

    /** 一轮问答：问题 + 候选人的回答（语音转写，可能为空）。 */
    public record Turn(String question, String answer) { }

    /**
     * 渲染成提示词里的"到目前为止这一题的问答"。
     *
     * 放在这里而不是 provider 里：它是纯字符串拼装，单测不需要起 Spring 就能钉住格式。
     */
    public String renderTurns() {
        if (turns == null || turns.isEmpty()) return "（还没有可参考的问答）";
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < turns.size(); index++) {
            Turn turn = turns.get(index);
            String label = index == 0 ? "原题" : "第 " + index + " 次追问";
            lines.add("%d. [%s] 问：%s".formatted(index + 1, label, blank(turn.question())));
            lines.add("   答：%s".formatted(blank(turn.answer())));
        }
        return String.join("\n", lines);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "（没有内容）" : value.strip();
    }
}
