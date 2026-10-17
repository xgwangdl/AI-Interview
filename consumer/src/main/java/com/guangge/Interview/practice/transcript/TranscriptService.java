package com.guangge.Interview.practice.transcript;

import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.entity.PracticeTranscriptRevisionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.repository.PracticeTranscriptRevisionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * 转写纠错与重评分。
 *
 * 只改文本、不改视频；原始转写保留，重评分覆盖当前分数但保留审计。
 */
@Service
public class TranscriptService {

    static final int MAX_REVISIONS = 3;

    private final PracticeAnswerRepository answers;
    private final PracticeSessionRepository sessions;
    private final PracticeTranscriptRevisionRepository revisions;
    private final AnalysisJobService analysisJobs;

    public TranscriptService(PracticeAnswerRepository answers, PracticeSessionRepository sessions,
                             PracticeTranscriptRevisionRepository revisions, AnalysisJobService analysisJobs) {
        this.answers = answers;
        this.sessions = sessions;
        this.revisions = revisions;
        this.analysisJobs = analysisJobs;
    }

    @Transactional
    public TranscriptEditView edit(String ownerId, UUID answerId, String text) {
        PracticeAnswerEntity answer = requireOwned(ownerId, answerId);
        if (answer.getTranscriptText() == null || answer.getTranscriptText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这道题还没有转写，暂时不能修正");
        }
        long used = revisions.countByAnswerId(answerId);
        if (used >= MAX_REVISIONS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这道题最多修正 3 次");
        }
        String after = text.strip();
        if (after.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "转写不能为空");
        if (after.equals(answer.getTranscriptText())) {
            return view(answer, used);
        }

        String before = answer.getTranscriptText();
        if (answer.getTranscriptOriginal() == null) {
            answer.setTranscriptOriginal(before);
        }
        PracticeTranscriptRevisionEntity revision = new PracticeTranscriptRevisionEntity();
        revision.setId(UUID.randomUUID());
        revision.setAnswerId(answerId);
        revision.setOwnerId(ownerId);
        revision.setBeforeText(before);
        revision.setAfterText(after);
        revision.setEditedAt(Instant.now());
        revisions.save(revision);

        answer.setTranscriptText(after);
        answer.setTranscriptStatus("completed");
        answer.setTranscriptEditedAt(Instant.now());
        // 旧评分是基于旧文本的，先清掉；重评分成功后重新写入。
        answer.setEvaluationJson(null);
        answer.setEvaluationStatus("awaiting_provider");
        answers.save(answer);
        return view(answer, used + 1);
    }

    @Transactional
    public void reEvaluate(String ownerId, UUID answerId) {
        PracticeAnswerEntity answer = requireOwned(ownerId, answerId);
        if (answer.getTranscriptText() == null || answer.getTranscriptText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有转写就无法重新评分");
        }
        analysisJobs.reenqueueAnswer(answerId);
    }

    @Transactional(readOnly = true)
    public TranscriptEditView get(String ownerId, UUID answerId) {
        return view(requireOwned(ownerId, answerId), revisions.countByAnswerId(answerId));
    }

    private PracticeAnswerEntity requireOwned(String ownerId, UUID answerId) {
        PracticeAnswerEntity answer = answers.findById(answerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在"));
        PracticeSessionEntity session = sessions.findByIdAndOwnerId(answer.getSessionId(), ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在"));
        return answer;
    }

    private static TranscriptEditView view(PracticeAnswerEntity answer, long used) {
        return new TranscriptEditView(answer.getTranscriptText(), answer.getTranscriptOriginal(),
                answer.getTranscriptEditedAt(), used, MAX_REVISIONS);
    }

    public record TranscriptEditView(String transcriptText, String transcriptOriginal,
                                     Instant transcriptEditedAt, long revisions, int maxRevisions) { }
}
