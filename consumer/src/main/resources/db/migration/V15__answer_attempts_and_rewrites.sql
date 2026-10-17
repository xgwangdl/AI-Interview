-- Phase 3：回答改写 + 重录 + before/after。
--
-- 同一道题允许最多 3 个 attempt；报告只把 is_current 的那一条算作"当前答案"，
-- 旧 attempt 保留下来用于对照。
ALTER TABLE practice_answers
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN rewrite_text TEXT,
    ADD COLUMN rewrite_source VARCHAR(16),
    ADD COLUMN rewrite_at TIMESTAMPTZ;

ALTER TABLE practice_answers DROP CONSTRAINT IF EXISTS uq_practice_answers_session_question;
CREATE UNIQUE INDEX IF NOT EXISTS uq_practice_answers_session_question_attempt
    ON practice_answers (session_id, question_index, attempt);
CREATE INDEX IF NOT EXISTS idx_practice_answers_current
    ON practice_answers (session_id, question_index, is_current);

CREATE TABLE IF NOT EXISTS answer_rewrites (
    id           UUID PRIMARY KEY,
    answer_id    UUID         NOT NULL REFERENCES practice_answers(id) ON DELETE CASCADE,
    owner_id     VARCHAR(160) NOT NULL,
    rewrite_text TEXT         NOT NULL,
    source       VARCHAR(16)  NOT NULL,   -- ai / user
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_answer_rewrites_answer
    ON answer_rewrites (answer_id, created_at DESC);
