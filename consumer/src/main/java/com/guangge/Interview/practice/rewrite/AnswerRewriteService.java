package com.guangge.Interview.practice.rewrite;

import com.guangge.Interview.practice.entity.AnswerRewriteEntity;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.AnswerRewriteRepository;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.quota.ModelCallGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 回答改写：AI 生成 / 用户编辑 / 历史留档。 */
@Service
public class AnswerRewriteService {

    private final PracticeAnswerRepository answers;
    private final PracticeSessionRepository sessions;
    private final AnswerRewriteRepository rewrites;
    private final PracticeQuestionCatalog questions;
    private final AnswerRewriteProvider provider;
    private final AnswerRewriteValidator validator;
    private final ModelCallGuard modelCalls;

    public AnswerRewriteService(PracticeAnswerRepository answers, PracticeSessionRepository sessions,
                                AnswerRewriteRepository rewrites, PracticeQuestionCatalog questions,
                                AnswerRewriteProvider provider, AnswerRewriteValidator validator,
                                ModelCallGuard modelCalls) {
        this.answers = answers;
        this.sessions = sessions;
        this.rewrites = rewrites;
        this.questions = questions;
        this.provider = provider;
        this.validator = validator;
        this.modelCalls = modelCalls;
    }

    @Transactional
    public RewriteView generate(String ownerId, UUID answerId) {
        try {
            Context context = context(ownerId, answerId);
            String text = modelCalls.call(ownerId, "回答改写",
                    () -> provider.rewrite(context.session().getRole(), context.session().getJobDescription(),
                            context.question(), context.answer().getTranscriptText()));
            return save(context, text, "ai");
        } catch (AnswerRewriteException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    @Transactional
    public RewriteView edit(String ownerId, UUID answerId, String text) {
        try {
            Context context = context(ownerId, answerId);
            return save(context, text, "user");
        } catch (AnswerRewriteException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RewriteView get(String ownerId, UUID answerId) {
        Context context = context(ownerId, answerId);
        return view(context.answer());
    }

    private RewriteView save(Context context, String text, String source) {
        String validated = "user".equals(source)
                ? validator.validateUser(text)
                : validator.validate(text, context.answer().getTranscriptText());
        AnswerRewriteEntity history = new AnswerRewriteEntity();
        history.setId(UUID.randomUUID());
        history.setAnswerId(context.answer().getId());
        history.setOwnerId(context.session().getOwnerId());
        history.setRewriteText(validated);
        history.setSource(source);
        history.setCreatedAt(Instant.now());
        rewrites.save(history);

        context.answer().setRewriteText(validated);
        context.answer().setRewriteSource(source);
        context.answer().setRewriteAt(Instant.now());
        answers.save(context.answer());
        return view(context.answer());
    }

    private Context context(String ownerId, UUID answerId) {
        PracticeAnswerEntity answer = answers.findById(answerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在"));
        PracticeSessionEntity session = sessions.findByIdAndOwnerId(answer.getSessionId(), ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在"));
        if (answer.getTranscriptText() == null || answer.getTranscriptText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这道题还没有转写，暂时不能改写");
        }
        String question = questions.questionText(session.getQuestionsJson(), answer.getQuestionId());
        return new Context(answer, session, question);
    }

    private static RewriteView view(PracticeAnswerEntity answer) {
        return new RewriteView(answer.getId(), answer.getRewriteText(), answer.getRewriteSource(),
                answer.getRewriteAt());
    }

    private record Context(PracticeAnswerEntity answer, PracticeSessionEntity session, String question) { }

    public record RewriteView(UUID answerId, String rewriteText, String source, Instant updatedAt) { }
}
