package com.guangge.Interview.jobradar;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@Component
public class JobLeadStatusMachine {
    public static final String NEW = "new";
    public static final String EVALUATING = "evaluating";
    public static final String SHORTLISTED = "shortlisted";
    public static final String APPLIED = "applied";
    public static final String INTERVIEWING = "interviewing";
    public static final String OFFER = "offer";
    public static final String REJECTED = "rejected";
    public static final String SKIPPED = "skipped";
    public static final String ARCHIVED = "archived";
    public static final Set<String> ALL = Set.of(NEW, EVALUATING, SHORTLISTED, APPLIED,
            INTERVIEWING, OFFER, REJECTED, SKIPPED, ARCHIVED);

    private static final Map<String, Set<String>> USER_TRANSITIONS = Map.of(
            NEW, Set.of(SKIPPED, ARCHIVED),
            SHORTLISTED, Set.of(APPLIED, SKIPPED, ARCHIVED),
            APPLIED, Set.of(INTERVIEWING, REJECTED, ARCHIVED),
            INTERVIEWING, Set.of(OFFER, REJECTED, ARCHIVED),
            OFFER, Set.of(ARCHIVED),
            REJECTED, Set.of(ARCHIVED),
            SKIPPED, Set.of(ARCHIVED),
            EVALUATING, Set.of(ARCHIVED),
            ARCHIVED, Set.of());

    public String transitionByUser(String from, String to) {
        if (EVALUATING.equals(to)) {
            throw badTransition(from, to);
        }
        return transition(from, to);
    }

    /** Only the evaluation worker may enter evaluating or restore a previous pipeline status. */
    public String transitionByWorker(String from, String to) {
        if (EVALUATING.equals(to)) {
            if (ARCHIVED.equals(from)) throw badTransition(from, to);
            return to;
        }
        return transition(from, to);
    }

    public String finishEvaluation(String previousStatus, int score, String recommendation) {
        if (ARCHIVED.equals(previousStatus)) throw badTransition(previousStatus, EVALUATING);
        if (previousStatus == null || NEW.equals(previousStatus) || EVALUATING.equals(previousStatus)) {
            return score >= 65 ? SHORTLISTED : SKIPPED;
        }
        if (!ALL.contains(previousStatus)) throw badTransition(previousStatus, EVALUATING);
        return previousStatus;
    }

    /** Restore a lead after retries are exhausted, without overriding a concurrent user decision. */
    public String recoverFailedEvaluation(String currentStatus, String previousStatus) {
        if (!EVALUATING.equals(currentStatus)) return currentStatus;
        if (previousStatus != null && ALL.contains(previousStatus)
                && !EVALUATING.equals(previousStatus) && !ARCHIVED.equals(previousStatus)) {
            return previousStatus;
        }
        return NEW;
    }

    private String transition(String from, String to) {
        if (from == null || to == null || !ALL.contains(from) || !ALL.contains(to)) {
            throw badTransition(from, to);
        }
        if (!USER_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw badTransition(from, to);
        }
        return to;
    }

    private static ResponseStatusException badTransition(String from, String to) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "非法岗位状态流转：" + from + " -> " + to);
    }
}
