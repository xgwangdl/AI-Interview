CREATE TABLE IF NOT EXISTS practice_sessions (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(160) NOT NULL,
    role VARCHAR(160) NOT NULL,
    company VARCHAR(200),
    job_description TEXT,
    resume_name VARCHAR(255),
    interview_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    interviewer_style VARCHAR(32) NOT NULL,
    question_count INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    questions_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    retention_until TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_practice_sessions_owner_created
    ON practice_sessions (owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_practice_sessions_retention
    ON practice_sessions (retention_until);

CREATE TABLE IF NOT EXISTS practice_answers (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES practice_sessions(id) ON DELETE CASCADE,
    question_index INTEGER NOT NULL,
    question_id VARCHAR(64) NOT NULL,
    media_key VARCHAR(100),
    media_content_type VARCHAR(100),
    media_size_bytes BIGINT,
    duration_seconds INTEGER,
    started_at_offset_seconds INTEGER,
    transcript_status VARCHAR(32) NOT NULL DEFAULT 'awaiting_provider',
    transcript_text TEXT,
    evaluation_status VARCHAR(32) NOT NULL DEFAULT 'awaiting_provider',
    evaluation_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_practice_answers_session_question UNIQUE (session_id, question_index)
);

CREATE INDEX IF NOT EXISTS idx_practice_answers_session
    ON practice_answers (session_id, question_index);
