package com.guangge.Interview.avatar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * First-phase 2D half-body interviewer. It never makes the interview depend on a
 * third-party avatar service: when speech synthesis is switched off or fails, the
 * client receives the caption only and degrades to a plain avatar.
 */
@Component
@ConditionalOnProperty(name = "practice.avatar.provider", havingValue = "local-2d", matchIfMissing = true)
public class LocalAvatarProvider implements AvatarProvider {

    private static final String PROVIDER = "local-2d";
    /** 单次合成的文本上限：Sambert 单次请求有长度限制，超了会整段失败。 */
    private static final int DEFAULT_MAX_SPEECH_CHARS = 300;

    private final TtsProvider ttsProvider;
    private final boolean speechEnabled;
    private final String voice;
    private final int maxSpeechChars;

    public LocalAvatarProvider(
            TtsProvider ttsProvider,
            @Value("${practice.avatar.tts-enabled:true}") boolean speechEnabled,
            @Value("${practice.avatar.voice:sambert-zhixiang-v1}") String voice,
            @Value("${practice.avatar.max-speech-chars:300}") int maxSpeechChars) {
        this.ttsProvider = ttsProvider;
        this.speechEnabled = speechEnabled;
        this.voice = voice;
        this.maxSpeechChars = Math.max(50, maxSpeechChars);
    }

    @Override
    public AvatarNarration narrate(AvatarNarrationRequest request) {
        String spoken = truncateForSpeech(request.text(), maxSpeechChars);
        return new AvatarNarration(
                PROVIDER,
                AvatarState.ASKING,
                request.text(),
                request.interviewerStyle(),
                speechEnabled,
                speechEnabled ? request.audioPath() : null,
                speechEnabled ? voice : null,
                speechEnabled && !spoken.equals(request.text()));
    }

    @Override
    public Optional<SpeechAudio> speech(AvatarNarrationRequest request) {
        if (!speechEnabled) return Optional.empty();
        return Optional.of(ttsProvider.synthesize(truncateForSpeech(request.text(), maxSpeechChars)));
    }

    /**
     * 超长文本按**句子边界**截断，而不是硬切：
     * 示范回答通常有好几句，硬切会在半句处断掉，听感很差。
     */
    static String truncateForSpeech(String text, int limit) {
        if (text == null) return "";
        if (text.length() <= limit) return text;

        String head = text.substring(0, limit);
        int cut = Math.max(head.lastIndexOf('。'), Math.max(head.lastIndexOf('！'), head.lastIndexOf('？')));
        if (cut < limit / 3) {
            // 前半段里没有句号（例如英文或没有标点），退而求其次找逗号，再不行就硬切
            cut = Math.max(head.lastIndexOf('，'), Math.max(head.lastIndexOf('；'), head.lastIndexOf(',')));
        }
        return cut > 0 ? head.substring(0, cut + 1) : head;
    }
}
