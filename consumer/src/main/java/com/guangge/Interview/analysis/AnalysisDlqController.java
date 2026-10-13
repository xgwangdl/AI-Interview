package com.guangge.Interview.analysis;

import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 死信队列的人工恢复入口（#10）。
 *
 * 系统目前没有角色模型，所以这里按「数据归属」过滤：只能看到并重新入队自己会话的死信任务。
 * 需要真正的管理员视图时，再引入角色并放开归属限制。
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisDlqController {

    private final OwnerResolver owners;
    private final PracticeSessionRepository sessions;
    private final AnalysisJobService analysisJobs;

    public AnalysisDlqController(OwnerResolver owners,
                                 PracticeSessionRepository sessions,
                                 AnalysisJobService analysisJobs) {
        this.owners = owners;
        this.sessions = sessions;
        this.analysisJobs = analysisJobs;
    }

    @GetMapping("/dlq")
    public List<AnalysisDlqView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit) {
        String owner = owners.requireOwner(authorization);
        return analysisJobs.listDlq(ownedSessionIds(owner), limit).stream()
                .map(AnalysisDlqView::from)
                .toList();
    }

    @PostMapping("/dlq/{jobId}/requeue")
    public AnalysisRequeueResult requeue(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID jobId) {
        String owner = owners.requireOwner(authorization);
        AnalysisDlqEntity entry = analysisJobs.findDlq(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead letter entry not found"));
        if (!ownedSessionIds(owner).contains(entry.getSessionId())) {
            // 与其它接口一致：不属于自己的资源返回 404，不泄露是否存在。
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead letter entry not found");
        }
        AnalysisJobEntity job = analysisJobs.requeueFromDlq(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Dead letter entry could not be requeued"));
        return new AnalysisRequeueResult(job.getId(), job.getSessionId(), job.getAnswerId(),
                job.getStatus(), job.getAttempts(), job.getNextAttemptAt());
    }

    private Set<UUID> ownedSessionIds(String owner) {
        return sessions.findAllByOwnerIdOrderByCreatedAtDesc(owner).stream()
                .map(PracticeSessionEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
