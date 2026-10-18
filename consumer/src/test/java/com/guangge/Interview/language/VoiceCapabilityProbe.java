package com.guangge.Interview.language;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.audio.services.AudioConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 外语音色与识别能力探针（不在 CI 里跑）。
 *
 * 为什么要有它：需求文档 §1 把"日文识别 / 英文合成 / 日文合成"列为上线前置条件。
 * 这三件事没法靠猜 —— 现在固定用的是中文音色 {@code sambert-zhixiang-v1}，
 * 换音色还要确认"模型是否存在、音色是否存在、日语汉字读得对不对"。
 *
 * 做法：对每个候选音色合成一句英文/日文，再把这句音频送回 ASR 做**往返测试** ——
 * 合成与识别只要有一环不支持这门语言，往返文本就会明显对不上。
 *
 *     mvn -B test -Dvoice.probe=true -Dtest=VoiceCapabilityProbe
 *
 * 产物：data/voice-probe/*.wav（可以直接听）+ 控制台的逐项结果。
 */
@EnabledIfSystemProperty(named = "voice.probe", matches = "true")
class VoiceCapabilityProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AudioConverter audioConverter = new AudioConverter("ffmpeg", 60);
    private static final String EN_TEXT = "I led the billing migration and cut the checkout latency from nine hundred to one hundred milliseconds.";
    private static final String JA_TEXT = "はじめまして。昨日、取引先と見積書の締め切りについて話しました。";

    /** (模型, 音色参数, 说明)。音色为空表示模型自带单一音色。 */
    private record Candidate(String model, String voice, String label) { }

    @Test
    void probesSynthesisAndRecognition() throws Exception {
        String apiKey = apiKey();
        Path out = Path.of("..", "data", "voice-probe");
        Files.createDirectories(out);
        System.out.println("输出目录: " + out.toAbsolutePath());

        List<Candidate> candidates = new ArrayList<>(List.of(
                new Candidate("sambert-zhixiang-v1", null, "sambert 男声(现状)"),
                new Candidate("sambert-zhichu-v1", null, "sambert 女声"),
                new Candidate("cosyvoice-v1", "longxiaochun", "cosyvoice 龙小春"),
                new Candidate("cosyvoice-v1", "loongstella", "cosyvoice Stella(英)"),
                new Candidate("cosyvoice-v1", "loongbella", "cosyvoice Bella(英)"),
                new Candidate("cosyvoice-v2", "longxiaochun_v2", "cosyvoice2 龙小春"),
                new Candidate("cosyvoice-v2", "longwan_v2", "cosyvoice2 龙婉"),
                new Candidate("qwen-tts", "Cherry", "qwen-tts Cherry"),
                new Candidate("qwen-tts", "Ethan", "qwen-tts Ethan")
        ));

        for (Candidate candidate : candidates) {
            probeOne(apiKey, candidate, "en", EN_TEXT, out);
            probeOne(apiKey, candidate, "ja", JA_TEXT, out);
        }
    }

    /**
     * 用**真人录的**日语片段测识别（上面的往返测试分不清"合成念错了"和"识别不支持"）。
     *
     *     mvn -B test -Dvoice.probe.audio="../data/voice-probe/ja-arigato.oga,../data/voice-probe/ja-icecream.ogg" \
     *          -Dtest=VoiceCapabilityProbe#recognizesJapaneseClips
     */
    @Test
    @EnabledIfSystemProperty(named = "voice.probe.audio", matches = ".+")
    void recognizesJapaneseClips() throws Exception {
        String apiKey = apiKey();
        for (String path : System.getProperty("voice.probe.audio").split(",")) {
            File source = new File(path.strip());
            File wav = audioConverter.convertToWav(source);
            try {
                for (String hint : List.of("ja", "zh")) {
                    long startedAt = System.currentTimeMillis();
                    try {
                        String transcript = recognize(apiKey, wav, hint);
                        System.out.printf("[real-ja] %s hint=%s → %dms：%s%n",
                                source.getName(), hint, System.currentTimeMillis() - startedAt, transcript);
                    } catch (Exception exception) {
                        System.out.printf("[real-ja] %s hint=%s → 失败：%s%n", source.getName(), hint, brief(exception));
                    }
                }
            } finally {
                Files.deleteIfExists(wav.toPath());
            }
        }
    }

    private void probeOne(String apiKey, Candidate candidate, String language, String text, Path out) {
        String tag = candidate.model() + (candidate.voice() == null ? "" : "/" + candidate.voice());
        String label = "[" + language + "] " + candidate.label() + " (" + tag + ")";
        long startedAt = System.currentTimeMillis();
        try {
            byte[] audio = synthesize(apiKey, candidate, text);
            long ttsMs = System.currentTimeMillis() - startedAt;
            String file = "%s-%s.wav".formatted(candidate.model() + "-" + (candidate.voice() == null ? "default" : candidate.voice()),
                    language);
            File wav = out.resolve(file).toFile();
            Files.write(wav.toPath(), audio);

            File resampled = audioConverter.convertToWav(wav);
            long asrStarted = System.currentTimeMillis();
            String transcript = recognize(apiKey, resampled, language);
            long asrMs = System.currentTimeMillis() - asrStarted;
            Files.deleteIfExists(resampled.toPath());

            boolean looksRight = language.equals("ja")
                    ? transcript.contains("見積") || transcript.contains("取引")
                    : transcript.toLowerCase().contains("billing") || transcript.toLowerCase().contains("latency");
            System.out.printf("%s → 合成 %dms / %d 字节，识别 %dms，往返%s%n    %s%n",
                    label, ttsMs, audio.length, asrMs, looksRight ? "对得上" : "对不上", transcript);
        } catch (Exception exception) {
            System.out.printf("%s → 失败（%dms）：%s%n", label,
                    System.currentTimeMillis() - startedAt, brief(exception));
        }
    }

    private byte[] synthesize(String apiKey, Candidate candidate, String text) {
        SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(candidate.model())
                .text(text)
                .sampleRate(48000);
        if (candidate.voice() != null) builder.parameter("voice", candidate.voice());
        return new SpeechSynthesizer().call(builder.build()).array();
    }

    private String recognize(String apiKey, File wav, String language) throws Exception {
        RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model("paraformer-realtime-v2")
                .format("wav")
                .sampleRate(16000)
                .parameter("language_hints", new String[]{language})
                .build();
        String raw = new Recognition().call(param, wav);
        JsonNode root = MAPPER.readTree(raw);
        JsonNode sentences = root.get("sentences");
        return sentences == null || sentences.isEmpty() ? "（没有句子：" + shorten(raw) + "）"
                : sentences.get(0).path("text").asText();
    }

    /** 从仓库根目录的 .env 读 key：探针不启动 Spring，也不想让 key 出现在命令行里。 */
    private static String apiKey() throws Exception {
        for (Path candidate : List.of(Path.of("..", ".env"), Path.of(".env"))) {
            if (!Files.exists(candidate)) continue;
            for (String line : Files.readAllLines(candidate)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("Ali_API_KEY=")) return trimmed.substring("Ali_API_KEY=".length()).strip();
            }
        }
        throw new IllegalStateException("没有在 .env 里找到 Ali_API_KEY");
    }

    private static String brief(Throwable throwable) {
        String message = throwable.getMessage();
        String text = throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return shorten(text);
    }

    private static String shorten(String value) {
        return value == null || value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
