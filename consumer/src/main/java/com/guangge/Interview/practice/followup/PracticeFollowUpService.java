package com.guangge.Interview.practice.followup;

import com.guangge.Interview.analysis.AsrProvider;
import com.guangge.Interview.practice.dto.PracticeFollowUpView;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.storage.MediaStorage;
import com.guangge.Interview.quota.ModelCallGuard;
import com.guangge.Interview.quota.ModelLease;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 追问式面试：面试官听完这一题的回答，决定要不要接着问一句。
 *
 * 与批量分析的分工：分析是整场结束后跑的（转写 + 评分 + 报告），
 * 追问必须在**答完这道题当场**给出，所以这里对单题做一次同步转写 + 一次模型判断。
 * 代价是这一题要多等几秒（前端会显示"面试官正在考虑是否追问"），
 * 收益是模拟面试不再只是"念题机器"。
 */
@Service
public class PracticeFollowUpService {

    private static final Logger log = LoggerFactory.getLogger(PracticeFollowUpService.class);
    /** 一场最多追问几次：再多就变成审问了，成本也顶不住。 */
    static final int MAX_FOLLOW_UPS_PER_SESSION = 3;
    /**
     * 同一道原题最多追问到第几轮。
     *
     * 连续追问要能看出"挖到底了"和"原地打转"的区别：第二轮允许（上一轮没问出细节，
     * 换个角度再要一次数字或取舍），第三轮就停 —— 再问下去只是在消耗用户耐心。
     */
    static final int MAX_ROUNDS_PER_QUESTION = 2;
    /** 转写短于这个字数就不问了 —— 没听清或者根本没答，追问也问不出东西。 */
    static final int MIN_TRANSCRIPT_CHARS = 12;
    static final String FOLLOW_UP_CATEGORY = PracticeQuestionCatalog.FOLLOW_UP_CATEGORY;

    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final MediaStorage mediaStorage;
    private final AsrProvider asrProvider;
    private final PracticeFollowUpProvider provider;
    private final PracticeQuestionCatalog questions;
    private final PracticeSessionService sessionService;
    private final ModelCallGuard modelCalls;
    private final long timeoutMs;

    public PracticeFollowUpService(
            PracticeSessionRepository sessions,
            PracticeAnswerRepository answers,
            MediaStorage mediaStorage,
            AsrProvider asrProvider,
            PracticeFollowUpProvider provider,
            PracticeQuestionCatalog questions,
            PracticeSessionService sessionService,
            ModelCallGuard modelCalls,
            @Value("${practice.follow-up.timeout-ms:30000}") long timeoutMs) {
        this.sessions = sessions;
        this.answers = answers;
        this.mediaStorage = mediaStorage;
        this.asrProvider = asrProvider;
        this.provider = provider;
        this.questions = questions;
        this.sessionService = sessionService;
        this.modelCalls = modelCalls;
        this.timeoutMs = Math.max(3000, timeoutMs);
    }

    public PracticeFollowUpView followUp(String ownerId, UUID sessionId, UUID answerId) {
        // 追问 = 一次转写 + 一次模型判断，两样都花钱。
        // 额度用完时不报错、只少问一句 —— 练习必须能继续走下去。
        try (ModelLease lease = modelCalls.tryAcquire(ownerId, "追问")) {
            if (!lease.allowed()) {
                return PracticeFollowUpView.none(lease.reason() + "，先答下一题吧");
            }
            return decide(ownerId, sessionId, answerId);
        }
    }

    private PracticeFollowUpView decide(String ownerId, UUID sessionId, UUID answerId) {
        PracticeSessionEntity session = sessions.findByIdAndOwnerId(sessionId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice session not found"));
        if (!session.isFollowUpEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这次练习没有开启追问");
        }
        PracticeAnswerEntity answer = answers.findById(answerId)
                .filter(item -> item.getSessionId().equals(sessionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));

        if (countFollowUps(session) >= MAX_FOLLOW_UPS_PER_SESSION) {
            return PracticeFollowUpView.none("这场练习的追问已经够多了，接着答下一题吧");
        }

        List<PracticeQuestion> questionList = questions.questions(session.getQuestionsJson());
        // 题号以 question_id 为准：questionIndex 只是位置，插过追问之后它俩可能不同步。
        int answeredIndex = PracticeQuestionCatalog.indexOf(questionList, answer.getQuestionId());
        int nextRound = answeredIndex < 0 ? 1
                : PracticeQuestionCatalog.followUpRoundOf(questionList, answeredIndex) + 1;
        if (nextRound > MAX_ROUNDS_PER_QUESTION) {
            return PracticeFollowUpView.none(
                    "这道题已经追问过 " + MAX_ROUNDS_PER_QUESTION + " 次，先答下一题吧");
        }

        String transcript = transcriptOf(answer);
        if (transcript == null || transcript.strip().length() < MIN_TRANSCRIPT_CHARS) {
            return PracticeFollowUpView.none("这段回答太短，没有可以深挖的内容");
        }

        PracticeFollowUpContext context = context(session, questionList, answeredIndex, answer, transcript, nextRound);
        PracticeFollowUpResult result = withTimeout(() -> provider.followUp(context));
        if (result.followUp() == null) {
            return PracticeFollowUpView.none(result.reason() == null
                    ? "回答已经比较完整，就不追问了" : result.reason());
        }

        PracticeQuestion question = sessionService.appendFollowUpQuestion(
                sessionId, answeredIndex, answer.getQuestionId(), result.followUp(), FOLLOW_UP_CATEGORY);
        log.info("练习 {} 生成第 {} 轮追问：{}（题目 {}）", sessionId, nextRound,
                LogSanitizer.truncate(result.followUp(), 60), question.id());
        return new PracticeFollowUpView(question, result.reason(), provider.modelName(), nextRound);
    }

    /**
     * 把"原题 + 到目前为止的每一轮问答"整理成上下文。
     *
     * 题干与转写都从库里现取：追问链跨了好几次请求，只有库里的才是真的。
     */
    private PracticeFollowUpContext context(PracticeSessionEntity session, List<PracticeQuestion> questionList,
                                            int answeredIndex, PracticeAnswerEntity answer,
                                            String transcript, int nextRound) {
        String rootQuestion = questions.questionText(session.getQuestionsJson(), answer.getQuestionId());
        int rootIndex = PracticeQuestionCatalog.rootIndexOf(questionList, answeredIndex);
        if (answeredIndex < 0 || rootIndex < 0) {
            return new PracticeFollowUpContext(session.getRole(), session.getJobDescription(),
                    rootQuestion, List.of(new PracticeFollowUpContext.Turn(rootQuestion, transcript)), nextRound);
        }

        Map<Integer, String> transcripts = new HashMap<>();
        for (PracticeAnswerEntity item : answers.findAllBySessionIdOrderByQuestionIndexAsc(session.getId())) {
            transcripts.put(item.getQuestionIndex(), item.getTranscriptText());
        }
        rootQuestion = questionList.get(rootIndex).text();

        List<PracticeFollowUpContext.Turn> turns = new ArrayList<>();
        for (int index = rootIndex; index <= answeredIndex && index < questionList.size(); index++) {
            String text = questionList.get(index).text();
            String spoken = index == answeredIndex ? transcript : transcripts.get(index);
            turns.add(new PracticeFollowUpContext.Turn(text, spoken));
        }
        return new PracticeFollowUpContext(session.getRole(), session.getJobDescription(),
                rootQuestion, turns, nextRound);
    }

    /** 优先用分析已经转写好的文本；没有就现转一次（这是"当场追问"的代价）。 */
    private String transcriptOf(PracticeAnswerEntity answer) {
        String existing = answer.getTranscriptText();
        if (existing != null && existing.strip().length() >= MIN_TRANSCRIPT_CHARS) return existing;
        if (answer.getMediaKey() == null) return existing;
        Resource media = mediaStorage.find(answer.getMediaKey()).orElse(null);
        if (media == null) return existing;
        String transcript = withTimeout(() -> asrProvider.transcribe(media, answer.getMediaContentType()).text());
        if (transcript != null && !transcript.isBlank()) {
            sessionService.saveTranscript(answer.getId(), transcript);
        }
        return transcript;
    }

    private static int countFollowUps(PracticeSessionEntity session) {
        // 追问题目的 id 形如 q-1-follow-1，数一下就行（不用改库结构）
        String json = session.getQuestionsJson();
        if (json == null || json.isBlank()) return 0;
        int count = 0;
        int index = json.indexOf("\"follow_up\"");
        while (index >= 0) {
            count++;
            index = json.indexOf("\"follow_up\"", index + 1);
        }
        return count;
    }

    private <T> T withTimeout(ThrowingCall<T> call) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return call.get();
                } catch (Exception exception) {
                    throw new PracticeFollowUpException(exception.getMessage(), exception);
                }
            }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (PracticeFollowUpException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "追问失败了：" + LogSanitizer.truncate(exception.getMessage(), 120) + "（可以继续答下一题）");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "追问超时了（" + timeoutMs / 1000 + " 秒），可以继续答下一题");
        }
    }

    private interface ThrowingCall<T> {
        T get() throws Exception;
    }
}
