package com.guangge.Interview.audio.services;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.guangge.Interview.util.JacksonMapperUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class SpeechToTextService {
    @Value("${spring.ai.dashscope.audio.transcription.api-key}")
    private String apiKey;

    /**
     * 音频转文字
     * @param audioFile 音频
     * @return 文字
     * @throws IOException
     */
    public String transcribeAudio(File audioFile) throws IOException {
        String result = "";
        // 创建Recognition实例
        Recognition recognizer = new Recognition();
        // 创建RecognitionParam
        RecognitionParam param =
                RecognitionParam.builder()
                        // 若没有将API Key配置到环境变量中，需将下面这行代码注释放开，并将apiKey替换为自己的API Key
                        .apiKey(apiKey)
                        .model("paraformer-realtime-v2")
                        .format("wav")
                        .sampleRate(16000)
                        // “language_hints”只支持paraformer-v2和paraformer-realtime-v2模型
                        .parameter("language_hints", new String[]{"zh", "en"})
                        .build();

        try {
            result = recognizer.call(param, audioFile);
            Map<String, Object> stringObjectMap = JacksonMapperUtils.json2map(result);
            List<Map<String, Object>> sentences = (List<Map<String, Object>>) stringObjectMap.get("sentences");
            result = (String) sentences.get(0).get("text");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
