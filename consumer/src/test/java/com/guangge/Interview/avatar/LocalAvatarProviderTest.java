package com.guangge.Interview.avatar;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAvatarProviderTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    @Test
    void narratesTheQuestionWithAnAudioUrlWhenSpeechIsEnabled() {
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1");

        AvatarNarration narration = provider.narrate(request());

        assertEquals("local-2d", narration.provider());
        assertEquals(AvatarState.ASKING, narration.state());
        assertEquals("讲一个你主导的重构", narration.text());
        assertTrue(narration.audioAvailable());
        assertEquals("sambert-zhixiang-v1", narration.voice());
        assertEquals("/api/v1/practice-sessions/" + SESSION_ID + "/questions/2/narration/audio",
                narration.audioUrl());
    }

    @Test
    void degradesToCaptionOnlyWhenSpeechIsDisabled() {
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), false, "sambert-zhixiang-v1");

        AvatarNarration narration = provider.narrate(request());

        assertFalse(narration.audioAvailable());
        assertNull(narration.audioUrl());
        assertNull(narration.voice());
        assertEquals("讲一个你主导的重构", narration.text());
    }

    @Test
    void doesNotSynthesizeSpeechWhenDisabled() {
        LocalAvatarProvider provider = new LocalAvatarProvider(
                text -> {
                    throw new AssertionError("speech synthesis must not run when disabled");
                },
                false, "sambert-zhixiang-v1");

        assertEquals(Optional.empty(), provider.speech(request()));
    }

    @Test
    void returnsSynthesizedAudioWhenEnabled() {
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1");

        SpeechAudio audio = provider.speech(request()).orElseThrow();

        assertEquals("audio/wav", audio.contentType());
        assertEquals(3, audio.audio().length);
    }

    private static AvatarNarrationRequest request() {
        return new AvatarNarrationRequest(SESSION_ID, 2, "讲一个你主导的重构", "balanced", "Java 后端");
    }

    private static TtsProvider stubTts() {
        return text -> new SpeechAudio(new byte[] {1, 2, 3}, "audio/wav");
    }
}
