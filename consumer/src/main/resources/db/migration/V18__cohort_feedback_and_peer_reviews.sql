-- 6B：学员求助 + 助教反馈。
CREATE TABLE IF NOT EXISTS cohort_help_requests (
    id            UUID PRIMARY KEY,
    cohort_id     UUID         NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    owner_id      VARCHAR(160) NOT NULL,
    session_id    UUID         NOT NULL,
    question_index INTEGER     NOT NULL,
    answer_id     UUID         NOT NULL,
    note          TEXT,
    status        VARCHAR(16)  NOT NULL,  -- open / answered / closed
    feedback_text TEXT,
    coach_id      VARCHAR(160),
    created_at    TIMESTAMPTZ  NOT NULL,
    answered_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_help_cohort_status
    ON cohort_help_requests (cohort_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_help_owner
    ON cohort_help_requests (owner_id, created_at DESC);

-- 6C：匿名同伴互评。默认关闭；开启后才允许别人看到你的转写并互评。
ALTER TABLE cohort_members ADD COLUMN peer_review_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS peer_reviews (
    id          UUID PRIMARY KEY,
    cohort_id   UUID         NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    reviewer_id VARCHAR(160) NOT NULL,
    target_owner_id VARCHAR(160) NOT NULL,
    answer_id   UUID         NOT NULL,
    clarity     INTEGER      NOT NULL,
    relevance   INTEGER      NOT NULL,
    depth       INTEGER      NOT NULL,
    suggestion  TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_peer_review_once
    ON peer_reviews (cohort_id, reviewer_id, answer_id);
CREATE INDEX IF NOT EXISTS idx_peer_review_target
    ON peer_reviews (cohort_id, target_owner_id, created_at DESC);
