package com.guangge.Interview.audio.services;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.guangge.Interview.util.JacksonMapperUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class SpeechToTextService {

    private final String apiKey;
    private final RecognitionInvoker invoker;

    @Autowired
    public SpeechToTextService(
            @Value("${spring.ai.dashscope.audio.transcription.api-key:}") String apiKey) {
        this(apiKey, (param, audioFile) -> new Recognition().call(param, audioFile));
    }

    SpeechToTextService(String apiKey, RecognitionInvoker invoker) {
        this.apiKey = apiKey;
        this.invoker = invoker;
    }

    /**
     * 音频转文字。
     *
     * 任何供应商调用或响应解析失败都必须抛异常，不能伪装成"识别结果为空"。
     */
    public String transcribeAudio(File audioFile) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("未配置语音识别 Key");
        }

        RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model("paraformer-realtime-v2")
                .format("wav")
                .sampleRate(16000)
                .parameter("language_hints", new String[]{"zh", "en"})
                .build();

        String raw;
        try {
            raw = invoker.recognize(param, audioFile);
        } catch (Exception exception) {
            throw new IOException("语音识别服务调用失败", exception);
        }
        return extractFirstSentence(raw);
    }

    static String extractFirstSentence(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("语音识别服务返回空响应");
        }

        Map<String, Object> root;
        try {
            root = JacksonMapperUtils.json2map(raw);
        } catch (Exception exception) {
            throw new IOException("语音识别响应不是有效 JSON", exception);
        }
        if (root == null) {
            throw new IOException("语音识别响应不是有效 JSON");
        }

        Object sentenceValue = root.get("sentences");
        if (!(sentenceValue instanceof List<?> sentences) || sentences.isEmpty()) {
            throw new IOException("语音识别响应中没有可用句子");
        }
        Object first = sentences.get(0);
        if (!(first instanceof Map<?, ?> sentence)) {
            throw new IOException("语音识别响应中的句子格式不正确");
        }

        Object text = sentence.get("text");
        String transcript = text == null ? "" : String.valueOf(text).strip();
        if (transcript.isBlank()) {
            throw new IOException("语音识别没有返回有效文本");
        }
        return transcript;
    }

    @FunctionalInterface
    interface RecognitionInvoker {
        String recognize(RecognitionParam param, File audioFile) throws Exception;
    }
}
