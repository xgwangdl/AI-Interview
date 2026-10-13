package com.guangge.Interview.analysis;

import java.time.Instant;
import java.util.UUID;

/** 死信任务重新入队后的结果，避免调用方把「入队前的快照」误读成当前状态。 */
public record AnalysisRequeueResult(
        UUID jobId,
        UUID sessionId,
        UUID answerId,
        String status,
        int attempts,
        Instant nextAttemptAt) {
}
