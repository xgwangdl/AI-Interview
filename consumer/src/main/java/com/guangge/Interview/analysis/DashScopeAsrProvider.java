package com.guangge.Interview.analysis;

import com.guangge.Interview.audio.services.AudioConverter;
import com.guangge.Interview.audio.services.SpeechToTextService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Adapter over the existing DashScope (Paraformer) speech recognition client. */
@Component
@ConditionalOnProperty(name = "practice.asr.provider", havingValue = "dashscope", matchIfMissing = true)
public class DashScopeAsrProvider implements AsrProvider {

    private final SpeechToTextService speechToText;
    private final AudioConverter audioConverter;

    public DashScopeAsrProvider(SpeechToTextService speechToText, AudioConverter audioConverter) {
        this.speechToText = speechToText;
        this.audioConverter = audioConverter;
    }

    @Override
    public TranscriptResult transcribe(Resource media, String contentType) throws IOException {
        LocalMedia source = materialize(media);
        try {
            // Answers are stored as video, so the audio track is extracted before recognition.
            File audio = toWav(source.file());
            try {
                String text = speechToText.transcribeAudio(audio);
                if (text == null || text.isBlank()) {
                    throw new IOException("Speech recognition returned no text");
                }
                return TranscriptResult.ofText(text);
            } finally {
                Files.deleteIfExists(audio.toPath());
            }
        } finally {
            if (source.temporary()) {
                Files.deleteIfExists(source.file().toPath());
            }
        }
    }

    /** Local storage hands back a real file; object storage is copied to a temp file. */
    private static LocalMedia materialize(Resource media) throws IOException {
        try {
            return new LocalMedia(media.getFile(), false);
        } catch (IOException | RuntimeException notAFile) {
            File temporary = File.createTempFile("answer-media-", ".media");
            try (InputStream content = media.getInputStream()) {
                Files.copy(content, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return new LocalMedia(temporary, true);
        }
    }

    private File toWav(File media) throws IOException {
        try {
            return audioConverter.convertToWav(media);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while extracting the answer audio", exception);
        }
    }

    private record LocalMedia(File file, boolean temporary) { }
}
