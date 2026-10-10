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

    private final TtsProvider ttsProvider;
    private final boolean speechEnabled;
    private final String voice;

    public LocalAvatarProvider(
            TtsProvider ttsProvider,
            @Value("${practice.avatar.tts-enabled:true}") boolean speechEnabled,
            @Value("${practice.avatar.voice:sambert-zhixiang-v1}") String voice) {
        this.ttsProvider = ttsProvider;
        this.speechEnabled = speechEnabled;
        this.voice = voice;
    }

    @Override
    public AvatarNarration narrate(AvatarNarrationRequest request) {
        return new AvatarNarration(
                PROVIDER,
                AvatarState.ASKING,
                request.questionText(),
                request.interviewerStyle(),
                speechEnabled,
                speechEnabled ? audioUrl(request) : null,
                speechEnabled ? voice : null);
    }

    @Override
    public Optional<SpeechAudio> speech(AvatarNarrationRequest request) {
        if (!speechEnabled) return Optional.empty();
        return Optional.of(ttsProvider.synthesize(request.questionText()));
    }

    private static String audioUrl(AvatarNarrationRequest request) {
        return "/api/v1/practice-sessions/" + request.sessionId()
                + "/questions/" + request.questionIndex() + "/narration/audio";
    }
}
