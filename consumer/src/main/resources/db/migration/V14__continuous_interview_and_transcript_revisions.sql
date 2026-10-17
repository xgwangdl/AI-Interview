-- Phase 2：连续面试模式 + 转写纠错/重评分。
ALTER TABLE practice_sessions
    ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'per_question',
    ADD COLUMN paused_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE practice_answers
    ADD COLUMN transcript_original TEXT,
    ADD COLUMN transcript_edited_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS answer_transcript_revisions (
    id          UUID PRIMARY KEY,
    answer_id   UUID         NOT NULL REFERENCES practice_answers(id) ON DELETE CASCADE,
    owner_id    VARCHAR(160) NOT NULL,
    before_text TEXT,
    after_text  TEXT         NOT NULL,
    edited_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transcript_revisions_answer
    ON answer_transcript_revisions (answer_id, edited_at DESC);
