-- #10：分析队列的可观测性、指数退避与死信队列（DLQ）

-- 指数退避：记录「下次允许重试的时间」。调度器只领取已到期的任务，
-- 避免失败任务每 15 秒被立刻重试一次、把供应商的限流放大成雪崩。
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;

UPDATE analysis_jobs SET next_attempt_at = created_at WHERE next_attempt_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_due
    ON analysis_jobs (status, next_attempt_at);

-- 死信队列：达到最大重试次数后把任务转存到这里，保留上下文供人工排查与重新入队。
-- analysis_jobs 里那一行会保持 status='failed'，因为报告页要靠它显示 analysis_failed。
-- 外键带 ON DELETE CASCADE：删除练习时会连 DLQ 记录一起清掉，不留隐私尾巴。
CREATE TABLE IF NOT EXISTS analysis_jobs_dlq (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES practice_sessions(id) ON DELETE CASCADE,
    answer_id UUID NOT NULL REFERENCES practice_answers(id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL,
    last_error TEXT,
    original_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    moved_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_dlq_moved_at
    ON analysis_jobs_dlq (moved_at DESC);
