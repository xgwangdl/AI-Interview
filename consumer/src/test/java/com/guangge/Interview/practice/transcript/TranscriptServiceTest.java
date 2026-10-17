package com.guangge.Interview.practice.transcript;

import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.entity.PracticeTranscriptRevisionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.repository.PracticeTranscriptRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 转写纠错：保留原文、限次数、清旧评分、触发重评分。 */
class TranscriptServiceTest {

    private final PracticeAnswerRepository answers = mock(PracticeAnswerRepository.class);
    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeTranscriptRevisionRepository revisions = mock(PracticeTranscriptRevisionRepository.class);
    private final AnalysisJobService analysisJobs = mock(AnalysisJobService.class);
    private final TranscriptService service = new TranscriptService(answers, sessions, revisions, analysisJobs);

    private PracticeAnswerEntity answer;
    private PracticeSessionEntity session;

    @BeforeEach
    void setUp() {
        session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setOwnerId("smoke");
        answer = new PracticeAnswerEntity();
        answer.setId(UUID.randomUUID());
        answer.setSessionId(session.getId());
        answer.setTranscriptStatus("completed");
        answer.setTranscriptText("I lead the billing migration.");
        answer.setEvaluationStatus("completed");
        answer.setEvaluationJson("{\"result\":{}}");
        answer.setCreatedAt(Instant.now());
        when(answers.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(sessions.findByIdAndOwnerId(session.getId(), "smoke")).thenReturn(Optional.of(session));
        when(revisions.countByAnswerId(answer.getId())).thenReturn(0L);
        when(answers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("第一次修正保留原文、写审计、清掉旧评分")
    void firstEditKeepsOriginalAndClearsEvaluation() {
        var view = service.edit("smoke", answer.getId(), "I led the billing migration.");

        assertThat(answer.getTranscriptText()).isEqualTo("I led the billing migration.");
        assertThat(answer.getTranscriptOriginal()).isEqualTo("I lead the billing migration.");
        assertThat(answer.getEvaluationJson()).isNull();
        assertThat(answer.getEvaluationStatus()).isEqualTo("awaiting_provider");
        assertThat(view.revisions()).isEqualTo(1);

        ArgumentCaptor<PracticeTranscriptRevisionEntity> captor =
                ArgumentCaptor.forClass(PracticeTranscriptRevisionEntity.class);
        verify(revisions).save(captor.capture());
        assertThat(captor.getValue().getBeforeText()).isEqualTo("I lead the billing migration.");
        assertThat(captor.getValue().getAfterText()).isEqualTo("I led the billing migration.");
    }

    @Test
    @DisplayName("最多修正 3 次，第 4 次直接 409")
    void stopsAfterThreeEdits() {
        when(revisions.countByAnswerId(answer.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.edit("smoke", answer.getId(), "fourth"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("重评分只重排队这一题；没有转写时拒绝")
    void reEvaluatesOnlyAnsweredTranscripts() {
        service.reEvaluate("smoke", answer.getId());
        verify(analysisJobs).reenqueueAnswer(answer.getId());

        answer.setTranscriptText(" ");
        assertThatThrownBy(() -> service.reEvaluate("smoke", answer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
