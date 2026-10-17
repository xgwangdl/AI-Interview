package com.guangge.Interview.language;

import java.util.List;

/** 一轮对话的结果：听写、回复、最多两条建议，以及可以回放的音频地址。 */
public record LanguageTurnView(
        int turnIndex,
        String transcript,
        String replyText,
        List<LanguageFeedback> feedback,
        String audioUrl,
        String replyAudioUrl,
        /** 没有服务端音色（日语）时为 true：前端用浏览器语音念 replyText。 */
        boolean browserVoice,
        Latency latency,
        String status) {

    public record Latency(Integer asrMs, Integer llmMs, Integer ttsMs, Integer totalMs) { }
}
