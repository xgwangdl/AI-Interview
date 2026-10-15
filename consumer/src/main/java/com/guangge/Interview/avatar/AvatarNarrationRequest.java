package com.guangge.Interview.avatar;

import java.util.UUID;

/**
 * 要播报的内容。
 *
 * {@code text} 是**实际要念出来的文本**，不一定等于题目原文：报告页的"示范回答"复用的是同一套
 * 播报链路，只是把文本换成模型给的优秀回答示例。
 * {@code audioPath} 由调用方给出，这样两条链路各自的音频地址都对得上 ——
 * 之前这个地址在 Provider 里写死成 narration 路径，复用时会把示范回答指向"念题目"的音频。
 */
public record AvatarNarrationRequest(
        UUID sessionId,
        int questionIndex,
        String text,
        String interviewerStyle,
        String role,
        String audioPath) { }
