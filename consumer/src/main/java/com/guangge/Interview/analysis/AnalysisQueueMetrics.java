package com.guangge.Interview.analysis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 分析队列的指标（#10）。
 *
 * Prometheus 暴露名（Micrometer 会把点号换成下划线）：
 * <ul>
 *   <li>{@code analysis_queue_length} —— 待处理任务数</li>
 *   <li>{@code analysis_processing_latency_ms} —— 单次处理耗时分布</li>
 *   <li>{@code analysis_failure_rate} —— 失败率（失败 / 已结束）</li>
 *   <li>{@code analysis_retry_count} —— 重试次数</li>
 *   <li>{@code analysis_dlq_size} —— 死信队列条目数</li>
 * </ul>
 *
 * 失败率用 Prometheus 侧算也行（failed_total / (completed_total + failed_total)），
 * 这里额外给一个现成 gauge，方便直接看板。
 */
@Component
public class AnalysisQueueMetrics {

    private static final Logger log = LoggerFactory.getLogger(AnalysisQueueMetrics.class);

    private final AnalysisJobRepository jobs;
    private final AnalysisDlqRepository dlq;

    private final Counter enqueued;
    private final Counter completed;
    private final Counter failed;
    private final Counter retried;
    private final Counter movedToDlq;
    private final DistributionSummary processingLatencyMs;

    private final AtomicLong completedTotal = new AtomicLong();
    private final AtomicLong failedTotal = new AtomicLong();

    public AnalysisQueueMetrics(MeterRegistry registry, AnalysisJobRepository jobs, AnalysisDlqRepository dlq) {
        this.jobs = jobs;
        this.dlq = dlq;

        this.enqueued = Counter.builder("analysis.jobs.enqueued")
                .description("分析任务入队次数")
                .register(registry);
        this.completed = Counter.builder("analysis.jobs.completed")
                .description("分析任务成功完成次数")
                .register(registry);
        this.failed = Counter.builder("analysis.jobs.failed")
                .description("分析任务失败次数（含重试后最终失败）")
                .register(registry);
        this.retried = Counter.builder("analysis.retry.count")
                .description("分析任务重试次数")
                .register(registry);
        this.movedToDlq = Counter.builder("analysis.jobs.dlq")
                .description("移入死信队列的任务数")
                .register(registry);
        this.processingLatencyMs = DistributionSummary.builder("analysis.processing.latency.ms")
                .description("单次任务处理耗时")
                .register(registry);

        Gauge.builder("analysis.queue.length", this, AnalysisQueueMetrics::pendingCount)
                .description("待处理的分析任务数")
                .register(registry);
        Gauge.builder("analysis.dlq.size", this, AnalysisQueueMetrics::dlqCount)
                .description("死信队列条目数")
                .register(registry);
        Gauge.builder("analysis.failure.rate", this, AnalysisQueueMetrics::failureRate)
                .description("失败率 = 失败 / (成功 + 失败)")
                .register(registry);
    }

    public void recordEnqueued(int count) {
        if (count > 0) enqueued.increment(count);
    }

    public void recordCompleted(long latencyMs) {
        completed.increment();
        completedTotal.incrementAndGet();
        processingLatencyMs.record(Math.max(0, latencyMs));
    }

    public void recordFailed(long latencyMs) {
        failed.increment();
        failedTotal.incrementAndGet();
        processingLatencyMs.record(Math.max(0, latencyMs));
    }

    public void recordRetry() {
        retried.increment();
    }

    public void recordMovedToDlq() {
        movedToDlq.increment();
    }

    private double pendingCount() {
        try {
            return jobs.countByStatus(AnalysisJobService.STATUS_PENDING);
        } catch (RuntimeException exception) {
            log.debug("读取待处理任务数失败: {}", exception.getMessage());
            return -1;
        }
    }

    private double dlqCount() {
        try {
            return dlq.count();
        } catch (RuntimeException exception) {
            log.debug("读取死信队列长度失败: {}", exception.getMessage());
            return -1;
        }
    }

    private double failureRate() {
        long ok = completedTotal.get();
        long bad = failedTotal.get();
        long total = ok + bad;
        return total == 0 ? 0 : (double) bad / total;
    }
}
