package com.guangge.Interview.audio.services;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechToTextServiceTest {

    @Test
    void extractsTheFirstRecognizedSentence() throws Exception {
        SpeechToTextService service = new SpeechToTextService("test-key",
                (param, audio) -> "{\"sentences\":[{\"text\":\" 你好世界 \"}]}");

        String result = service.transcribeAudio(new File("sample.wav"));

        assertEquals("你好世界", result);
    }

    @Test
    void providerFailureIsNotTurnedIntoAnEmptyTranscript() {
        SpeechToTextService service = new SpeechToTextService("test-key",
                (param, audio) -> {
                    throw new IllegalStateException("provider down");
                });

        IOException exception = assertThrows(IOException.class,
                () -> service.transcribeAudio(new File("sample.wav")));

        assertTrue(exception.getMessage().contains("调用失败"));
    }

    @Test
    void rejectsMissingEmptyAndMalformedSentences() {
        assertMessage(() -> SpeechToTextService.extractFirstSentence("{}"), "没有可用句子");
        assertMessage(() -> SpeechToTextService.extractFirstSentence("{\"sentences\":[]}"), "没有可用句子");
        assertMessage(() -> SpeechToTextService.extractFirstSentence("{\"sentences\":[{\"text\":\"  \"}]}"),
                "没有返回有效文本");
        assertMessage(() -> SpeechToTextService.extractFirstSentence("not-json"), "不是有效 JSON");
    }

    private static void assertMessage(ThrowingCall call, String expected) {
        IOException exception = assertThrows(IOException.class, call::run);
        assertTrue(exception.getMessage().contains(expected), exception.getMessage());
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
