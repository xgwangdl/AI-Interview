package com.guangge.Interview.language.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.language.LanguageCoach;
import com.guangge.Interview.language.LanguageCoachRequest;
import com.guangge.Interview.language.LanguageFeedback;
import com.guangge.Interview.language.LanguageSessionService;
import com.guangge.Interview.language.LanguageSpeechService;
import com.guangge.Interview.language.LanguageTurnEntity;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * L2 实时对话通道。
 *
 * 协议（都是 JSON 文本帧，音频帧是二进制）：
 *   客户端 → 服务端：`{"type":"start","sessionId":"..."}`、PCM16/16k 二进制帧、`{"type":"commit"}`、`{"type":"cancel"}`
 *   服务端 → 客户端：`ready / partial / transcript / reply / audio / feedback / turn / error`
 *
 * 与 L1 的区别只在"什么时候能看到东西"：识别边推边出、回复边生成边发、第一句生成完就开始合成语音。
 * 落库仍然是同一张 `language_turns`，所以报告、额度、清理逻辑完全复用。
 */
@Component
public class LanguageStreamHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LanguageStreamHandler.class);
    private static final int TTS_SAMPLE_RATE = 24000;
    private static final int MAX_TURN_MS = 90_000;

    private final LanguageSessionService sessions;
    private final LanguageSpeechService speech;
    private final LanguageCoach coach;
    private final ObjectMapper mapper;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public LanguageStreamHandler(LanguageSessionService sessions, LanguageSpeechService speech,
                                 LanguageCoach coach, ObjectMapper mapper) {
        this.sessions = sessions;
        this.speech = speech;
        this.coach = coach;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        states.put(session.getId(), new State(session));
        send(session, Map.of("type", "ready"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        State state = states.remove(session.getId());
        if (state != null) state.close();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        State state = states.get(session.getId());
        if (state == null) return;
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            switch (node.path("type").asText()) {
                case "start" -> start(session, state, node.path("sessionId").asText());
                case "commit" -> commit(session, state);
                case "cancel" -> state.cancel();
                default -> send(session, Map.of("type", "error", "message", "未知指令"));
            }
        } catch (Exception exception) {
            log.warn("外语实时通道处理失败 session={}: {}", session.getId(), exception.getMessage());
            send(session, Map.of("type", "error", "message", safe(exception.getMessage())));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        State state = states.get(session.getId());
        if (state == null || state.busy) return;
        byte[] frame = new byte[message.getPayloadLength()];
        message.getPayload().get(frame);
        synchronized (state) {
            state.pcm.write(frame, 0, frame.length);
        }
        state.asrPush(frame);
    }

    // ---------- 一轮 ----------

    private void start(WebSocketSession ws, State state, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("缺少 sessionId");
        state.reset(UUID.fromString(sessionId));
        // 先把识别通道打开：握手与用户开口重叠，commit 时就不用再等
        state.context = sessions.streamContext(state.owner, state.sessionId);
        sessions.requireSession(state.owner, state.sessionId);
        state.asr = speech.openStream(state.context.spec());
        state.asr.results().subscribe(result -> {
            String text = result.getSentence() == null ? null : result.getSentence().getText();
            if (text == null || text.isBlank()) return;
            state.transcript = text.strip();
            send(ws, Map.of("type", "partial", "text", state.transcript));
        }, error -> {
            state.asrError = safe(error.getMessage());
            log.warn("实时识别流失败 session={}: {}", state.sessionId, state.asrError);
            state.asrDone.countDown();
        }, state.asrDone::countDown);
        send(ws, Map.of("type", "listening"));
    }

    private void commit(WebSocketSession ws, State state) {
        if (state.sessionId == null || state.context == null) throw new IllegalStateException("还没有开始这一轮");
        if (state.busy) throw new IllegalStateException("上一轮还没说完");
        state.busy = true;
        state.commitAt = System.currentTimeMillis();
        state.asr.complete();
        try {
            if (!state.asrDone.await(4, TimeUnit.SECONDS)) state.asrError = "识别超时";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (state.transcript.isBlank()) {
            state.busy = false;
            send(ws, Map.of("type", "error", "message", state.asrError == null
                    ? "没有听清这一句，再说一次" : "识别失败：" + state.asrError));
            return;
        }
        send(ws, Map.of("type", "transcript", "text", state.transcript));
        state.asrMs = elapsed(state.commitAt);
        byte[] wav = wrapWav(state.pcm.toByteArray(), 16000);
        pool.submit(() -> runTurn(ws, state, wav));
    }

    private void runTurn(WebSocketSession ws, State state, byte[] wav) {
        long startedAt = state.commitAt;
        LanguageCoachRequest request = new LanguageCoachRequest(
                state.context.spec(), state.context.scenario(), state.context.level(), state.context.target(),
                history(state.context.turns()), state.transcript);
        try {
            StringBuilder reply = new StringBuilder();
            StringBuilder pending = new StringBuilder();
            coach.streamReply(request, delta -> {
                if (state.cancelled) return;
                if (state.llmFirstMs == 0) state.llmFirstMs = elapsed(startedAt);
                reply.append(delta);
                send(ws, Map.of("type", "reply", "delta", delta));
                pending.append(delta);
                String sentence = takeSentence(pending);
                if (!sentence.isEmpty()) state.enqueueSpeech(sentence, startedAt, ws, speech);
            });
            if (!pending.toString().isBlank()) state.enqueueSpeech(pending.toString().strip(), startedAt, ws, speech);
            int llmMs = elapsed(startedAt);
            String fullReply = reply.toString().strip();
            if (fullReply.isEmpty()) throw new IllegalStateException("模型没有返回回复");

            LanguageSessionService.SavedTurn saved = sessions.saveStreamedTurn(
                    state.owner, state.sessionId, state.transcript, fullReply, List.of(), wav,
                    state.asrMs, state.llmFirstMs == 0 ? llmMs : state.llmFirstMs, llmMs,
                    state.audioFirstMs == 0 ? null : state.audioFirstMs,
                    state.audioFirstMs == 0 ? llmMs : state.audioFirstMs);
            send(ws, Map.of("type", "turn",
                    "turnIndex", saved.view().turnIndex(),
                    "replyText", fullReply,
                    "latency", Map.of(
                            "asrMs", state.asrMs,
                            "llmFirstMs", state.llmFirstMs,
                            "llmMs", llmMs,
                            "audioFirstMs", state.audioFirstMs,
                            "totalMs", state.audioFirstMs == 0 ? llmMs : state.audioFirstMs)));
            state.busy = false;

            pool.submit(() -> {
                try {
                    List<LanguageFeedback> feedback = coach.feedback(request);
                    sessions.attachFeedback(saved.turnId(), feedback);
                    send(ws, Map.of("type", "feedback", "items", feedback));
                } catch (RuntimeException exception) {
                    log.info("实时反馈生成失败（不影响对话）: {}", exception.getMessage());
                }
            });
        } catch (Exception exception) {
            state.busy = false;
            log.warn("实时一轮失败 session={}: {}", state.sessionId, exception.getMessage());
            send(ws, Map.of("type", "error", "message", safe(exception.getMessage())));
        }
    }

    // ---------- 工具 ----------

    private static String takeSentence(StringBuilder buffer) {
        int cut = -1;
        for (int index = 0; index < buffer.length(); index++) {
            char value = buffer.charAt(index);
            boolean terminal = value == '.' || value == '!' || value == '?' || value == '。' || value == '！' || value == '？';
            if (!terminal) continue;
            // 跳过 3.5 / U.S. 这类点
            if (value == '.' && index + 1 < buffer.length() && Character.isDigit(buffer.charAt(index + 1))) continue;
            cut = index;
        }
        if (cut < 0) return "";
        String sentence = buffer.substring(0, cut + 1).strip();
        buffer.delete(0, cut + 1);
        return sentence;
    }

    private static List<LanguageCoachRequest.Exchange> history(List<LanguageTurnEntity> turns) {
        List<LanguageCoachRequest.Exchange> exchanges = new ArrayList<>();
        int from = Math.max(0, turns.size() - 4);
        for (LanguageTurnEntity turn : turns.subList(from, turns.size())) {
            exchanges.add(new LanguageCoachRequest.Exchange(turn.getTranscript(), turn.getReplyText()));
        }
        return exchanges;
    }

    private void send(WebSocketSession ws, Map<String, ?> payload) {
        try {
            String json = mapper.writeValueAsString(new LinkedHashMap<>(payload));
            synchronized (ws) {
                if (ws.isOpen()) ws.sendMessage(new TextMessage(json));
            }
        } catch (Exception exception) {
            log.debug("实时通道发送失败: {}", exception.getMessage());
        }
    }

    private static String safe(String message) {
        return LogSanitizer.truncate(LogSanitizer.redact(message == null ? "未知错误" : message), 160);
    }

    private static int elapsed(long startedAt) {
        return (int) (System.currentTimeMillis() - startedAt);
    }

    /** 16k/16bit/单声道 PCM → WAV（44 字节头），用于回放与落库。 */
    static byte[] wrapWav(byte[] pcm, int sampleRate) {
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(36 + pcm.length);
        buffer.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(pcm.length);
        buffer.put(pcm);
        return buffer.array();
    }

    /** 每条连接的状态（一轮一轮复用）。 */
    private final class State {
        private final WebSocketSession ws;
        private final String owner;
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private final AtomicInteger audioSeq = new AtomicInteger();
        private final CountDownLatch asrDone = new CountDownLatch(1);

        private UUID sessionId;
        private LanguageSessionService.StreamContext context;
        private LanguageSpeechService.AsrStream asr;
        private volatile String transcript = "";
        private volatile String asrError;
        private volatile boolean busy;
        private volatile boolean cancelled;
        private volatile long commitAt;
        private volatile int llmFirstMs;
        private volatile int audioFirstMs;
        private volatile int asrMs;
        private volatile CompletableFuture<Void> speechChain = CompletableFuture.completedFuture(null);

        private State(WebSocketSession ws) {
            this.ws = ws;
            this.owner = (String) ws.getAttributes().get(LanguageHandshakeInterceptor.OWNER_ATTRIBUTE);
        }

        private void reset(UUID id) {
            sessionId = id;
            context = null;
            transcript = "";
            asrError = null;
            busy = false;
            cancelled = false;
            llmFirstMs = 0;
            audioFirstMs = 0;
            asrMs = 0;
            pcm.reset();
            audioSeq.set(0);
            speechChain = CompletableFuture.completedFuture(null);
            if (asr != null) asr.complete();
            asr = null;
        }

        private void asrPush(byte[] frame) {
            if (asr == null) return;
            try {
                asr.push(frame);
            } catch (RuntimeException exception) {
                // 识别流已经结束（例如上游超时）时不能再往里塞帧，否则会把整条 WebSocket 弄断
                asrError = safe(exception.getMessage());
            }
        }

        private void enqueueSpeech(String sentence, long startedAt, WebSocketSession ws, LanguageSpeechService speechService) {
            speechChain = speechChain.thenRunAsync(() -> {
                if (cancelled) return;
                speechService.streamPcm(sentence, context.spec(), chunk -> {
                    if (cancelled) return;
                    if (audioFirstMs == 0) audioFirstMs = elapsed(startedAt);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "audio");
                    payload.put("seq", audioSeq.incrementAndGet());
                    payload.put("sampleRate", TTS_SAMPLE_RATE);
                    payload.put("data", Base64.getEncoder().encodeToString(chunk));
                    send(ws, payload);
                });
            }, pool);
        }

        private void cancel() {
            cancelled = true;
            busy = false;
            if (asr != null) asr.complete();
        }

        private void close() {
            cancel();
        }
    }
}
