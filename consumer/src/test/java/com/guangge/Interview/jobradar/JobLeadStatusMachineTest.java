package com.guangge.Interview.jobradar;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobLeadStatusMachineTest {
    private final JobLeadStatusMachine machine = new JobLeadStatusMachine();

    @Test
    void usersCannotManuallySetEvaluating() {
        assertThatThrownBy(() -> machine.transitionByUser("new", "evaluating"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void followsHappyPathIntoInterviewing() {
        assertThat(machine.transitionByWorker("new", "evaluating")).isEqualTo("evaluating");
        assertThat(machine.finishEvaluation("new", 78, "apply")).isEqualTo("shortlisted");
        assertThat(machine.transitionByUser("shortlisted", "applied")).isEqualTo("applied");
        assertThat(machine.transitionByUser("applied", "interviewing")).isEqualTo("interviewing");
    }

    @Test
    void archivedIsTerminal() {
        assertThatThrownBy(() -> machine.transitionByUser("archived", "new"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void lowScoreMovesNewLeadToSkipped() {
        assertThat(machine.finishEvaluation("new", 49, "skip")).isEqualTo("skipped");
    }

    @Test
    void failedEvaluationRestoresPreviousStatusWithoutOverwritingUserDecision() {
        assertThat(machine.recoverFailedEvaluation("evaluating", "shortlisted")).isEqualTo("shortlisted");
        assertThat(machine.recoverFailedEvaluation("evaluating", "evaluating")).isEqualTo("new");
        assertThat(machine.recoverFailedEvaluation("archived", "shortlisted")).isEqualTo("archived");
    }
}
