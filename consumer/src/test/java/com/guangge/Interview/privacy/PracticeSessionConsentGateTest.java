package com.guangge.Interview.privacy;

import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.goal.DailyPlanService;
import com.guangge.Interview.practice.controller.PracticeSessionController;
import com.guangge.Interview.practice.followup.PracticeFollowUpService;
import com.guangge.Interview.practice.media.MediaSignedUrlService;
import com.guangge.Interview.practice.rewrite.AnswerRewriteService;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.practice.service.PracticeReportService;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.transcript.TranscriptService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionConsentGateTest {

    @Test
    void startingAnInterviewRequiresRecordingConsentBeforeAnyRecordingCanBegin() {
        OwnerResolver owners = mock(OwnerResolver.class);
        RecordingConsentService consents = mock(RecordingConsentService.class);
        PracticeSessionService sessions = mock(PracticeSessionService.class);
        UUID sessionId = UUID.randomUUID();
        when(owners.requireOwner("Bearer token")).thenReturn("alice");
        doThrow(new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "RECORDING_CONSENT_REQUIRED"))
                .when(consents).requireRecordingConsent("alice");

        PracticeSessionController controller = new PracticeSessionController(
                sessions,
                mock(PracticeReportService.class),
                mock(AnalysisJobService.class),
                mock(MediaSignedUrlService.class),
                owners,
                mock(PracticeFollowUpService.class),
                mock(DailyPlanService.class),
                mock(TranscriptService.class),
                mock(AnswerRewriteService.class),
                consents);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.start("Bearer token", sessionId));

        assertEquals(HttpStatus.PRECONDITION_REQUIRED, error.getStatusCode());
        verify(sessions, org.mockito.Mockito.never()).start("alice", sessionId);
    }
}
