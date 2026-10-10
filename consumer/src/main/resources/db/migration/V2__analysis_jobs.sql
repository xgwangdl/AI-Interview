CREATE TABLE IF NOT EXISTS analysis_jobs (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES practice_sessions(id) ON DELETE CASCADE,
    answer_id UUID NOT NULL REFERENCES practice_answers(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_analysis_jobs_answer UNIQUE (answer_id)
);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status
    ON analysis_jobs (status, created_at);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_session
    ON analysis_jobs (session_id);
