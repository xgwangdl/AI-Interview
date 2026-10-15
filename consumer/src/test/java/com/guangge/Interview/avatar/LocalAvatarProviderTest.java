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
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1", 300);

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
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), false, "sambert-zhixiang-v1", 300);

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
                false, "sambert-zhixiang-v1", 300);

        assertEquals(Optional.empty(), provider.speech(request()));
    }

    @Test
    void returnsSynthesizedAudioWhenEnabled() {
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1", 300);

        SpeechAudio audio = provider.speech(request()).orElseThrow();

        assertEquals("audio/wav", audio.contentType());
        assertEquals(3, audio.audio().length);
    }

    @Test
    void reportsTheSampleAnswerAudioPathInsteadOfTheQuestionOne() {
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1", 300);
        AvatarNarration narration = provider.narrate(new AvatarNarrationRequest(
                SESSION_ID, 1, "示范回答正文", "balanced", "Java 后端",
                "/api/v1/practice-sessions/" + SESSION_ID + "/questions/1/sample-answer/audio"));

        assertEquals("示范回答正文", narration.text());
        assertEquals("/api/v1/practice-sessions/" + SESSION_ID + "/questions/1/sample-answer/audio",
                narration.audioUrl());
        assertFalse(narration.truncated());
    }

    @Test
    void cutsLongTextAtSentenceBoundaryAndFlagsItAsTruncated() {
        String sentence = "这是一句示范回答。";             // 9 字
        String longText = sentence.repeat(20);              // 180 字，超过下面给的 40 字上限
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1", 40);

        AvatarNarration narration = provider.narrate(new AvatarNarrationRequest(
                SESSION_ID, 0, longText, "balanced", "Java 后端", "/x"));

        // 播报文本是**实际念出来的那一段**（字幕要跟它对上），truncated 告诉前端"只念了前面一段"
        assertTrue(narration.text().length() < longText.length());
        assertTrue(longText.startsWith(narration.text()), narration.text());
        assertTrue(narration.truncated());
        String spoken = LocalAvatarProvider.truncateForSpeech(longText, 40);
        assertTrue(spoken.length() <= 40, spoken);
        assertTrue(spoken.endsWith("。"), spoken);
    }

    @Test
    void doesNotFlagShortTextAsTruncated() {
        LocalAvatarProvider provider = new LocalAvatarProvider(stubTts(), true, "sambert-zhixiang-v1", 300);
        AvatarNarration narration = provider.narrate(request());
        assertFalse(narration.truncated());
    }

    private static AvatarNarrationRequest request() {
        return new AvatarNarrationRequest(SESSION_ID, 2, "讲一个你主导的重构", "balanced", "Java 后端",
                "/api/v1/practice-sessions/" + SESSION_ID + "/questions/2/narration/audio");
    }

    private static TtsProvider stubTts() {
        return text -> new SpeechAudio(new byte[] {1, 2, 3}, "audio/wav");
    }
}
