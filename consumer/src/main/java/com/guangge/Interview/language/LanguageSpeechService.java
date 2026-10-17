package com.guangge.Interview.language;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.audio.services.AudioConverter;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 外语练习的转写与合成。
 *
 * 与面试模块的区别只有两点：**语言提示按会话传**（zh/en/ja），以及**没有日语服务端音色时明确返回空**，
 * 让前端改用浏览器语音 —— 而不是拿中文音色硬念日语。
 */
@Service
public class LanguageSpeechService {

    /** 单次合成的文本上限：Sambert/CosyVoice 对长度都有限制，超了整段失败。 */
    private static final int MAX_SPEECH_CHARS = 300;

    private final ObjectMapper objectMapper;
    private final String transcriptionKey;
    private final String speechKey;

    public LanguageSpeechService(
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.audio.transcription.api-key:}") String transcriptionKey,
            @Value("${spring.ai.dashscope.audio.speech.api-key:}") String speechKey) {
        this.objectMapper = objectMapper;
        this.transcriptionKey = transcriptionKey;
        this.speechKey = speechKey;
    }

    /**
     * 转写一段音频。
     *
     * 浏览器录的 webm/ogg 先由 ffmpeg 转成 16k 单声道 wav；识别结果按 {@code language_hints} 指定语言
     * （真人日语样本验证过：hint=ja 能正确出「ありがとう」，hint=zh 是乱码）。
     */
    public Transcript transcribe(File source, LanguageSpec spec) {
        if (transcriptionKey == null || transcriptionKey.isBlank()) {
            throw new LanguageSpeechException("没有配置语音识别 Key，暂时只能手打输入");
        }
        long startedAt = System.currentTimeMillis();
        File wav = null;
        boolean converted = false;
        try {
            // 前端现在直接录 16k 单声道 WAV：跳过 ffmpeg（省 0.3-0.8 秒）。
            // 其它格式仍然走转换，行为不变。
            if (isSixteenKMonoWav(source)) {
                wav = source;
            } else {
                wav = AudioConverter.convertToWav(source);
                converted = true;
            }
            RecognitionParam param = RecognitionParam.builder()
                    .apiKey(transcriptionKey)
                    .model("paraformer-realtime-v2")
                    .format("wav")
                    .sampleRate(16000)
                    .parameter("language_hints", new String[]{spec.asrHint()})
                    .build();
            String raw = new Recognition().call(param, wav);
            String text = firstSentence(raw);
            if (text == null || text.isBlank()) {
                throw new LanguageSpeechException("没有听清这一句");
            }
            return new Transcript(text.strip(), (int) (System.currentTimeMillis() - startedAt));
        } catch (LanguageSpeechException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LanguageSpeechException("语音识别失败了：" + brief(exception), exception);
        } finally {
            if (converted && wav != null) {
                try {
                    Files.deleteIfExists(wav.toPath());
                } catch (Exception ignored) {
                    // 临时文件删不掉不影响这一轮
                }
            }
        }
    }

    /**
     * 合成 AI 的回复。
     *
     * 没有服务端音色（日语）时返回 empty：这是"能力缺失"，不是"这次失败"，
     * 前端据此切到浏览器语音。
     */
    public Optional<byte[]> synthesize(String text, LanguageSpec spec) {
        if (!spec.serverVoice() || spec.ttsModel() == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (speechKey == null || speechKey.isBlank()) return Optional.empty();
        try {
            SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                    .apiKey(speechKey)
                    .model(spec.ttsModel())
                    .text(truncate(text, MAX_SPEECH_CHARS))
                    .sampleRate(48000);
            if (spec.ttsVoice() != null && !spec.ttsVoice().isBlank()) {
                builder.parameter("voice", spec.ttsVoice());
            }
            byte[] audio = new SpeechSynthesizer().call(builder.build()).array();
            return audio.length == 0 ? Optional.empty() : Optional.of(audio);
        } catch (RuntimeException exception) {
            // 合成失败不该让整轮对话失败：回复文字仍然有用，前端可以只显示文字
            return Optional.empty();
        }
    }

    private String firstSentence(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode sentences = root.get("sentences");
        return sentences == null || sentences.isEmpty() ? null : sentences.get(0).path("text").asText();
    }

    /**
     * 开一条实时识别流（L2）：音频按 PCM16 / 16k / 单声道 边推边出文字。
     *
     * 与文件模式共用同一个模型（`paraformer-realtime-v2`），区别只是"喂一段"变成"喂一帧"。
     */
    public AsrStream openStream(LanguageSpec spec) {
        if (transcriptionKey == null || transcriptionKey.isBlank()) {
            throw new LanguageSpeechException("没有配置语音识别 Key");
        }
        RecognitionParam param = RecognitionParam.builder()
                .apiKey(transcriptionKey)
                .model("paraformer-realtime-v2")
                .format("pcm")
                .sampleRate(16000)
                .parameter("language_hints", new String[]{spec.asrHint()})
                .build();
        return new AsrStream(new Recognition(), param);
    }

    /** 实时识别流：push 二进制帧，complete 后结果流会结束。 */
    public static final class AsrStream {
        private final PublishProcessor<ByteBuffer> input = PublishProcessor.create();
        private final Flowable<RecognitionResult> results;

        private AsrStream(Recognition recognition, RecognitionParam param) {
            try {
                this.results = recognition.streamCall(param, input);
            } catch (Exception exception) {
                throw new LanguageSpeechException("实时识别通道打不开：" + exception.getMessage(), exception);
            }
        }

        public void push(byte[] pcm) {
            if (input.hasComplete() || input.hasThrowable()) return;
            // 明确小端：PCM16 是 little-endian，ByteBuffer.wrap 默认是大端
            input.onNext(ByteBuffer.wrap(pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN));
        }

        public void complete() {
            if (!input.hasComplete() && !input.hasThrowable()) input.onComplete();
        }

        public Flowable<RecognitionResult> results() {
            return results;
        }
    }

    /**
     * 流式合成一句，按 PCM 帧回调（L2）。
     *
     * 一句一批而不是整段：第一句合成完就开始播，后面的句子边生成边接上。
     * 格式固定 24k/16bit/单声道——前端用同一个采样率排队播放。
     */
    public void streamPcm(String text, LanguageSpec spec, Consumer<byte[]> onChunk) {
        if (!spec.serverVoice() || spec.ttsModel() == null || text == null || text.isBlank()) return;
        if (speechKey == null || speechKey.isBlank()) return;
        SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                .apiKey(speechKey)
                .model(spec.ttsModel())
                .text(truncate(text, MAX_SPEECH_CHARS))
                .format(SpeechSynthesisAudioFormat.PCM)
                .sampleRate(24000);
        if (spec.ttsVoice() != null && !spec.ttsVoice().isBlank()) {
            builder.parameter("voice", spec.ttsVoice());
        }
        new SpeechSynthesizer().streamCall(builder.build()).blockingSubscribe(result -> {
            ByteBuffer frame = result.getAudioFrame();
            if (frame == null || !frame.hasRemaining()) return;
            byte[] bytes = new byte[frame.remaining()];
            frame.get(bytes);
            onChunk.accept(bytes);
        }, error -> { /* 单句合成失败不影响后面的句子，文字仍然在 */ });
    }

    /** 只认"我们自己录的"那种最朴素的 44 字节头 WAV：PCM / 单声道 / 16k / 16bit。 */
    static boolean isSixteenKMonoWav(File file) {
        if (file == null || file.length() < 44) return false;
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.BufferedInputStream(new java.io.FileInputStream(file)))) {
            byte[] header = new byte[44];
            if (in.read(header) != 44) return false;
            return new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")
                    && new String(header, 12, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("fmt ")
                    && littleShort(header, 20) == 1
                    && littleShort(header, 22) == 1
                    && littleInt(header, 24) == 16000
                    && littleShort(header, 34) == 16
                    && new String(header, 36, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("data");
        } catch (Exception exception) {
            return false;
        }
    }

    private static int littleShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int littleInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String brief(Throwable throwable) {
        String message = throwable.getMessage();
        String text = message == null ? throwable.getClass().getSimpleName() : message;
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }

    public record Transcript(String text, int elapsedMs) { }
}
