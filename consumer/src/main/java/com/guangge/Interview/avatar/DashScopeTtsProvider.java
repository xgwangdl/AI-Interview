package com.guangge.Interview.avatar;

import com.guangge.Interview.audio.services.TextToSpeechService;
import org.springframework.stereotype.Component;

/** Adapter over the existing DashScope (Sambert) synthesis client. */
@Component
public class DashScopeTtsProvider implements TtsProvider {

    private static final String CONTENT_TYPE = "audio/wav";

    private final TextToSpeechService textToSpeech;

    public DashScopeTtsProvider(TextToSpeechService textToSpeech) {
        this.textToSpeech = textToSpeech;
    }

    @Override
    public SpeechAudio synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Narration text is empty");
        }
        byte[] audio = textToSpeech.textToSpeech(text);
        if (audio == null || audio.length == 0) {
            throw new IllegalStateException("Speech synthesis returned no audio");
        }
        return new SpeechAudio(audio, CONTENT_TYPE);
    }
}
