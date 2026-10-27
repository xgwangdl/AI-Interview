package com.guangge.Interview.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.storage.MediaStorage;
import com.guangge.Interview.quota.ModelCallGuard;
import com.guangge.Interview.quota.ModelLease;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 外语口语会话的编排：一句一答，同步返回。
 *
 * 与面试模块最大的不同：**这里没有异步队列**。对话的每一轮都要在几秒内给出结果，
 * 走 `AnalysisJobService` 那套"入队 + 15 秒轮询"就变成留言板了。
 */
@Service
public class LanguageSessionService {

    private static final Logger log = LoggerFactory.getLogger(LanguageSessionService.class);
    public static final String ACTIVE = "active";
    public static final String FINISHED = "finished";
    public static final String ABANDONED = "abandoned";
    /** 给模型的上下文：最近 4 轮够用了，再长只会拖慢首句、也更贵。 */
    private static final int HISTORY_TURNS = 4;

    private final LanguageSessionRepository sessions;
    private final LanguageTurnRepository turns;
    private final LanguageCatalog catalog;
    private final LanguageCoach coach;
    private final LanguageSpeechService speech;
    private final MediaStorage mediaStorage;
    private final ModelCallGuard modelCalls;
    private final ObjectMapper objectMapper;
    private final int retentionDays;

    public LanguageSessionService(
            LanguageSessionRepository sessions,
            LanguageTurnRepository turns,
            LanguageCatalog catalog,
            LanguageCoach coach,
            LanguageSpeechService speech,
            MediaStorage mediaStorage,
            ModelCallGuard modelCalls,
            ObjectMapper objectMapper,
            @Value("${practice.language.retention-days:7}") int retentionDays) {
        this.sessions = sessions;
        this.turns = turns;
        this.catalog = catalog;
        this.coach = coach;
        this.speech = speech;
        this.mediaStorage = mediaStorage;
        this.modelCalls = modelCalls;
        this.objectMapper = objectMapper;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public LanguageSessionView create(String ownerId, LanguageSessionRequest request) {
        LanguageSpec spec = catalog.language(request.language())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持这门语言"));
        LanguageScenario scenario = catalog.scenario(spec.code(), request.scenario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个语言下没有该场景"));
        String level = LanguageCatalog.LEVELS.contains(request.level()) ? request.level() : "intermediate";
        int maxTurns = request.maxTurns() == null ? LanguageCatalog.DEFAULT_MAX_TURNS
                : Math.min(Math.max(request.maxTurns(), 1), LanguageCatalog.MAX_TURNS_LIMIT);

        // 同一账号只留一场进行中：新开一场就把旧的标成 abandoned（不是报错挡住用户，
        // 否则关掉页面再回来就再也开不了新的了）
        for (LanguageSessionEntity active : sessions.findAllByOwnerIdAndStatus(ownerId, ACTIVE)) {
            active.setStatus(ABANDONED);
            sessions.save(active);
        }

        Instant now = Instant.now();
        LanguageSessionEntity entity = new LanguageSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setLanguage(spec.code());
        entity.setScenario(scenario.id());
        entity.setLevel(level);
        entity.setTarget(scenario.goal());
        entity.setStatus(ACTIVE);
        entity.setTurnCount(0);
        entity.setMaxTurns(maxTurns);
        entity.setCreatedAt(now);
        entity.setRetentionUntil(now.plusSeconds(retentionDays * 86400L));
        sessions.save(entity);
        return view(entity, List.of());
    }

    @Transactional(readOnly = true)
    public List<LanguageSessionView> list(String ownerId) {
        List<LanguageSessionView> views = new ArrayList<>();
        for (LanguageSessionEntity entity : sessions.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)) {
            // 语言下线后旧会话还在库里（例如日语）：跳过它们，而不是让整个列表 500 ——
            // 一条历史记录不该把"开始新练习"的页面弄挂（用户反馈的"语言配置丢失"就是这个）。
            if (!configured(entity)) {
                log.info("跳过语言配置已下线的会话：{}（{}）", entity.getId(), entity.getLanguage());
                continue;
            }
            views.add(view(entity, turns.findAllBySessionIdOrderByTurnIndexAsc(entity.getId())));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public LanguageSessionView get(String ownerId, UUID id) {
        LanguageSessionEntity session = requireOwned(ownerId, id);
        if (!configured(session)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这次练习的语言已下线，换一场新的吧");
        }
        return view(session, turns.findAllBySessionIdOrderByTurnIndexAsc(id));
    }

    /** 语言与场景都还在注册表里，这个会话才能被打开。 */
    private boolean configured(LanguageSessionEntity session) {
        return catalog.language(session.getLanguage()).isPresent()
                && catalog.scenario(session.getLanguage(), session.getScenario()).isPresent();
    }

    /**
     * 一轮对话（音频）。
     *
     * 额度按"轮"计 1 次：转写、回复、合成三件事是同一次用户动作，分开扣会让用户看不懂为什么一次讲话扣三次。
     */
    @Transactional
    public LanguageTurnView turn(String ownerId, UUID id, File audio, String contentType, Integer audioMs)
            throws IOException {
        LanguageSessionEntity session = requireActive(ownerId, id);
        LanguageSpec spec = spec(session);
        LanguageScenario scenario = scenario(session);
        int index = session.getTurnCount() + 1;
        UUID turnId = UUID.randomUUID();

        try (ModelLease lease = reserve(ownerId)) {
            long startedAt = System.currentTimeMillis();
            long asrStarted = startedAt;
            LanguageSpeechService.Transcript transcript = speech.transcribe(audio, spec);
            int asrMs = (int) (System.currentTimeMillis() - asrStarted);

            long llmStarted = System.currentTimeMillis();
            LanguageCoachResult result = coach.reply(coachRequest(session, spec, scenario, transcript.text()));
            int llmMs = (int) (System.currentTimeMillis() - llmStarted);

            LanguageTurnEntity entity = new LanguageTurnEntity();
            entity.setId(turnId);
            entity.setSessionId(id);
            entity.setTurnIndex(index);
            storeAudio("lang-" + turnId + "-user", audio, contentType, entity::setAudioKey);
            entity.setAudioContentType(contentType);
            entity.setAudioMs(audioMs);
            entity.setTranscript(transcript.text());
            entity.setReplyText(result.reply());
            entity.setFeedbackJson(write(result.feedback()));
            entity.setAsrMs(asrMs);
            entity.setLlmMs(llmMs);
            // 配音不在这一轮里做：先把文字回给用户，音频点"听回复"时再合成（见 speech()）。
            // 这样 2 秒多的合成不再挡在"看到回复"前面。
            entity.setTotalMs((int) (System.currentTimeMillis() - startedAt));
            entity.setStatus("answered");
            entity.setCreatedAt(Instant.now());
            turns.save(entity);
            session.setTurnCount(index);
            sessions.save(session);
            log.info("外语对话 {}/{} 完成：{}ms（asr {} / llm {} / tts {}）", id, index,
                    entity.getTotalMs(), asrMs, llmMs, entity.getTtsMs());
            return turnView(entity, spec);
        } catch (LanguageSpeechException | LanguageCoachException exception) {
            // 这一轮没成，但会话必须能继续：把原因说清楚，不推进轮次
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    LogSanitizer.truncate(exception.getMessage(), 160) + "（可以再试一次，或先手打这一句）");
        }
    }

    /** 手打一轮：识别不可用、环境太吵、或者用户想练书面表达时的降级入口。 */
    @Transactional
    public LanguageTurnView turnText(String ownerId, UUID id, String text) throws IOException {
        LanguageSessionEntity session = requireActive(ownerId, id);
        LanguageSpec spec = spec(session);
        LanguageScenario scenario = scenario(session);
        String transcript = text.strip();
        if (transcript.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这一句是空的");
        }
        int index = session.getTurnCount() + 1;
        try (ModelLease lease = reserve(ownerId)) {
            long startedAt = System.currentTimeMillis();
            LanguageCoachResult result = coach.reply(coachRequest(session, spec, scenario, transcript));
            int llmMs = (int) (System.currentTimeMillis() - startedAt);
            UUID turnId = UUID.randomUUID();
            LanguageTurnEntity entity = new LanguageTurnEntity();
            entity.setId(turnId);
            entity.setSessionId(id);
            entity.setTurnIndex(index);
            entity.setTranscript(transcript);
            entity.setReplyText(result.reply());
            entity.setFeedbackJson(write(result.feedback()));
            entity.setLlmMs(llmMs);
            entity.setTotalMs((int) (System.currentTimeMillis() - startedAt));
            entity.setStatus("answered");
            entity.setCreatedAt(Instant.now());
            turns.save(entity);
            session.setTurnCount(index);
            sessions.save(session);
            return turnView(entity, spec);
        } catch (LanguageCoachException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    LogSanitizer.truncate(exception.getMessage(), 160) + "（可以再试一次）");
        }
    }

    /** 结束并出报告。已经出过报告的直接返回，不重复扣额度。 */
    @Transactional
    public LanguageSessionView finish(String ownerId, UUID id) {
        LanguageSessionEntity session = requireOwned(ownerId, id);
        List<LanguageTurnEntity> sessionTurns = turns.findAllBySessionIdOrderByTurnIndexAsc(id);
        if (sessionTurns.isEmpty() && session.getReportJson() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "至少说完一轮才能生成复盘");
        }
        if (session.getReportJson() == null) {
            LanguageSpec spec = spec(session);
            LanguageScenario scenario = scenario(session);
            List<LanguageReportRequest.Turn> reportTurns = sessionTurns.stream()
                    .map(turn -> new LanguageReportRequest.Turn(turn.getTurnIndex(), turn.getTranscript(),
                            turn.getReplyText()))
                    .toList();
            LanguageReportResult result = modelCalls.call(ownerId, "外语复盘",
                    () -> coach.report(new LanguageReportRequest(spec, scenario, session.getLevel(),
                            session.getTarget(), reportTurns)));
            session.setReportJson(write(result));
            session.setFinishedAt(Instant.now());
        }
        session.setStatus(FINISHED);
        sessions.save(session);
        return view(session, sessionTurns);
    }

    @Transactional
    public void delete(String ownerId, UUID id) throws IOException {
        LanguageSessionEntity session = requireOwned(ownerId, id);
        deleteWithMedia(session);
    }

    @Scheduled(cron = "0 27 3 * * *")
    @Transactional
    public void deleteExpired() {
        for (LanguageSessionEntity session : sessions.findAllByRetentionUntilBefore(Instant.now())) {
            try {
                deleteWithMedia(session);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to remove expired language media", exception);
            }
        }
    }

    /** 这一轮的用户录音 / AI 语音，供前端回放。 */
    @Transactional(readOnly = true)
    public MediaFile media(String ownerId, UUID id, int turnIndex, String side) {
        requireOwned(ownerId, id);
        LanguageTurnEntity turn = turns.findBySessionIdAndTurnIndex(id, turnIndex)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "这一轮不存在"));
        boolean reply = "reply".equalsIgnoreCase(side);
        String key = reply ? turn.getReplyAudioKey() : turn.getAudioKey();
        if (key == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这一轮没有音频");
        Resource resource = mediaStorage.find(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "音频已被清理"));
        return new MediaFile(resource, reply ? "audio/wav" : turn.getAudioContentType());
    }

    /**
     * AI 回复的语音，**按需合成**。
     *
     * 不放在 `turn()` 里的原因：合成要 2 秒多，而用户真正在意的是"多久看到回复"。
     * 文字先到，音频在用户点"听回复"（或前端自动请求）时再生成；同一轮只合成一次（落库复用）。
     * 合成费用已经算在这一轮的额度里，不重复扣。
     */
    @Transactional
    public MediaFile speech(String ownerId, UUID id, int turnIndex) throws IOException {
        LanguageSessionEntity session = requireOwned(ownerId, id);
        LanguageSpec spec = spec(session);
        if (!spec.serverVoice()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这门语言没有服务端音色");
        }
        LanguageTurnEntity turn = turns.findBySessionIdAndTurnIndex(id, turnIndex)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "这一轮不存在"));
        if (turn.getReplyAudioKey() == null) {
            long startedAt = System.currentTimeMillis();
            byte[] audio = speech.synthesize(turn.getReplyText(), spec)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音合成暂不可用，先看文字"));
            String key = "lang-" + turn.getId() + "-reply";
            mediaStorage.store(key, new java.io.ByteArrayInputStream(audio), audio.length);
            turn.setReplyAudioKey(key);
            turn.setTtsMs((int) (System.currentTimeMillis() - startedAt));
            turns.save(turn);
        }
        Resource resource = mediaStorage.find(turn.getReplyAudioKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "音频已被清理"));
        return new MediaFile(resource, "audio/wav");
    }

    /** 开场白没有独立的对话轮次，也需要一个可按需合成、可复用的服务端音色地址。 */
    @Transactional
    public MediaFile openingSpeech(String ownerId, UUID id) throws IOException {
        LanguageSessionEntity session = requireOwned(ownerId, id);
        LanguageSpec spec = spec(session);
        if (!spec.serverVoice()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这门语言没有服务端音色");
        }
        String key = openingAudioKey(id);
        if (mediaStorage.find(key).isEmpty()) {
            LanguageScenario scenario = scenario(session);
            byte[] audio = speech.synthesize(scenario.opening(), spec)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音合成暂不可用，先看文字"));
            mediaStorage.store(key, new java.io.ByteArrayInputStream(audio), audio.length);
        }
        Resource resource = mediaStorage.find(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "音频已被清理"));
        return new MediaFile(resource, "audio/wav");
    }

    private void deleteWithMedia(LanguageSessionEntity session) throws IOException {
        String openingKey = openingAudioKey(session.getId());
        if (mediaStorage.find(openingKey).isPresent()) mediaStorage.delete(openingKey);
        for (LanguageTurnEntity turn : turns.findAllBySessionIdOrderByTurnIndexAsc(session.getId())) {
            if (turn.getAudioKey() != null) mediaStorage.delete(turn.getAudioKey());
            if (turn.getReplyAudioKey() != null) mediaStorage.delete(turn.getReplyAudioKey());
        }
        sessions.delete(session);
    }

    private static String openingAudioKey(UUID id) {
        return "lang-" + id + "-opening";
    }

    private ModelLease reserve(String ownerId) {
        ModelLease lease = modelCalls.tryAcquire(ownerId, "外语对话");
        if (!lease.allowed()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, lease.reason());
        return lease;
    }

    private LanguageCoachRequest coachRequest(LanguageSessionEntity session, LanguageSpec spec,
                                              LanguageScenario scenario, String transcript) {
        List<LanguageTurnEntity> history = turns.findAllBySessionIdOrderByTurnIndexAsc(session.getId());
        List<LanguageCoachRequest.Exchange> exchanges = new ArrayList<>();
        for (LanguageTurnEntity turn : history.subList(Math.max(0, history.size() - HISTORY_TURNS), history.size())) {
            exchanges.add(new LanguageCoachRequest.Exchange(turn.getTranscript(), turn.getReplyText()));
        }
        return new LanguageCoachRequest(spec, scenario, session.getLevel(), session.getTarget(), exchanges, transcript);
    }

    private LanguageSessionEntity requireOwned(String ownerId, UUID id) {
        return sessions.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "这次练习不存在"));
    }

    private LanguageSessionEntity requireActive(String ownerId, UUID id) {
        LanguageSessionEntity session = requireOwned(ownerId, id);
        if (!ACTIVE.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这次练习已经结束了");
        }
        if (session.getTurnCount() >= session.getMaxTurns()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已经说满 " + session.getMaxTurns() + " 轮了，点结束看复盘");
        }
        return session;
    }

    private LanguageSpec spec(LanguageSessionEntity session) {
        return catalog.language(session.getLanguage())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "语言配置丢失"));
    }

    private LanguageScenario scenario(LanguageSessionEntity session) {
        return catalog.scenario(session.getLanguage(), session.getScenario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "场景配置丢失"));
    }

    private LanguageSessionView view(LanguageSessionEntity session, List<LanguageTurnEntity> sessionTurns) {
        LanguageSpec spec = spec(session);
        LanguageScenario scenario = scenario(session);
        return new LanguageSessionView(
                session.getId(), session.getLanguage(), spec.displayName(), spec.browserVoice(), spec.serverVoice(),
                session.getScenario(), session.getLevel(),
                session.getTarget(), session.getStatus(), session.getTurnCount(), session.getMaxTurns(),
                session.getCreatedAt(), session.getFinishedAt(), session.getRetentionUntil(),
                scenario.opening(),
                sessionTurns.stream().map(turn -> turnView(turn, spec)).toList(),
                report(session));
    }

    private LanguageTurnView turnView(LanguageTurnEntity turn, LanguageSpec spec) {
        return new LanguageTurnView(
                turn.getTurnIndex(), turn.getTranscript(), turn.getReplyText(),
                readFeedback(turn.getFeedbackJson()),
                turn.getAudioKey() == null ? null
                        : "/api/v1/language-sessions/" + turn.getSessionId() + "/turns/" + turn.getTurnIndex() + "/audio?side=user",
                // 有服务端音色就给"按需合成"的地址：文字先到，点听的时候再花那 2 秒
                !spec.serverVoice() ? null
                        : "/api/v1/language-sessions/" + turn.getSessionId() + "/turns/" + turn.getTurnIndex() + "/speech",
                !spec.serverVoice(),
                new LanguageTurnView.Latency(turn.getAsrMs(), turn.getLlmMs(), turn.getTtsMs(), turn.getTotalMs()),
                turn.getStatus());
    }

    private LanguageReportView report(LanguageSessionEntity session) {
        if (session.getReportJson() == null) return null;
        try {
            LanguageReportResult result = objectMapper.readValue(session.getReportJson(), LanguageReportResult.class);
            return new LanguageReportView(result.fluency(), result.vocabulary(), result.grammar(), result.task(),
                    result.summary(), result.improvements(), session.getFinishedAt());
        } catch (Exception exception) {
            return null;
        }
    }

    private List<LanguageFeedback> readFeedback(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, LanguageFeedback.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void storeAudio(String key, File audio, String contentType,
                            java.util.function.Consumer<String> applyKey) throws IOException {
        try (InputStream content = new java.io.FileInputStream(audio)) {
            mediaStorage.store(key, content, audio.length());
        }
        applyKey.accept(key);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化会话数据", exception);
        }
    }

    public record MediaFile(Resource resource, String contentType) { }

    // ---------- L2 流式对话用的入口 ----------

    /** 一场实时对话需要的静态上下文与历史。 */
    public record StreamContext(LanguageSpec spec, LanguageScenario scenario, String level, String target,
                                List<LanguageTurnEntity> turns) { }

    @Transactional(readOnly = true)
    public StreamContext streamContext(String ownerId, UUID id) {
        LanguageSessionEntity session = requireActive(ownerId, id);
        return new StreamContext(spec(session), scenario(session), session.getLevel(), session.getTarget(),
                turns.findAllBySessionIdOrderByTurnIndexAsc(id));
    }

    public UUID requireSession(String ownerId, UUID id) {
        return requireActive(ownerId, id).getId();
    }

    /**
     * 存一轮流式对话（音频是浏览器边录边推的 PCM，这里落成 WAV）。
     *
     * 反馈可能晚一点才到（L2 先播回复），所以先存轮次，反馈由 {@link #attachFeedback} 补上。
     */
    @Transactional
    public SavedTurn saveStreamedTurn(String ownerId, UUID id, String transcript, String reply,
                                      List<LanguageFeedback> feedback, byte[] wavPcm,
                                      Integer asrMs, Integer llmFirstMs, Integer llmMs,
                                      Integer audioFirstMs, Integer totalMs) throws IOException {
        LanguageSessionEntity session = requireActive(ownerId, id);
        LanguageSpec spec = spec(session);
        int index = session.getTurnCount() + 1;
        UUID turnId = UUID.randomUUID();
        LanguageTurnEntity entity = new LanguageTurnEntity();
        entity.setId(turnId);
        entity.setSessionId(id);
        entity.setTurnIndex(index);
        if (wavPcm != null && wavPcm.length > 44) {
            String key = "lang-" + turnId + "-user";
            mediaStorage.store(key, new java.io.ByteArrayInputStream(wavPcm), wavPcm.length);
            entity.setAudioKey(key);
            entity.setAudioContentType("audio/wav");
            entity.setAudioMs((int) Math.min(Integer.MAX_VALUE, (wavPcm.length - 44) / 32));
        }
        entity.setTranscript(transcript);
        entity.setReplyText(reply);
        entity.setFeedbackJson(write(feedback));
        entity.setAsrMs(asrMs);
        entity.setLlmFirstMs(llmFirstMs);
        entity.setLlmMs(llmMs);
        entity.setAudioFirstMs(audioFirstMs);
        entity.setTotalMs(totalMs);
        entity.setStatus("answered");
        entity.setCreatedAt(Instant.now());
        turns.save(entity);
        session.setTurnCount(index);
        sessions.save(session);
        return new SavedTurn(turnId, turnView(entity, spec));
    }

    @Transactional
    public void attachFeedback(UUID turnId, List<LanguageFeedback> feedback) {
        turns.findById(turnId).ifPresent(entity -> {
            entity.setFeedbackJson(write(feedback));
            turns.save(entity);
        });
    }

    public record SavedTurn(UUID turnId, LanguageTurnView view) { }
}
