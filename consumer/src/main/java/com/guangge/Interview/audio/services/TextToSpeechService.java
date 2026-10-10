package com.guangge.Interview.audio.services;


import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;


@Service
public class TextToSpeechService {
    @Value("${spring.ai.dashscope.audio.speech.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.audio.speech.options.model}")
    private String model;

    /**
     * 文字转音频
     * @param response 文字
     * @return 音频
     */
    public byte[] textToSpeech(String response) {
        SpeechSynthesizer synthesizer = new SpeechSynthesizer();
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .text(response)
                        .sampleRate(48000)
                        .build();
        ByteBuffer audio = synthesizer.call(param);
        return audio.array();
    }
}
