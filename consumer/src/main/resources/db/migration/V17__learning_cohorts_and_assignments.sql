-- Phase 6A：学习小组 / 班级 + 作业 + 完成看板。
CREATE TABLE IF NOT EXISTS cohorts (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    invite_code VARCHAR(16)  NOT NULL UNIQUE,
    owner_id    VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    archived_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS cohort_members (
    cohort_id    UUID         NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    owner_id     VARCHAR(160) NOT NULL,
    role         VARCHAR(16)  NOT NULL,   -- coach / learner
    share_scores BOOLEAN      NOT NULL DEFAULT FALSE,
    joined_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (cohort_id, owner_id)
);

CREATE INDEX IF NOT EXISTS idx_cohort_members_owner
    ON cohort_members (owner_id, joined_at DESC);

CREATE TABLE IF NOT EXISTS assignments (
    id            UUID PRIMARY KEY,
    cohort_id     UUID         NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    days          INTEGER      NOT NULL,
    items_per_day INTEGER      NOT NULL,
    due_at        TIMESTAMPTZ,
    created_by    VARCHAR(160) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_assignments_cohort
    ON assignments (cohort_id, created_at DESC);

-- 作业生成的每日训练项挂到 assignment_id；同一成员同一作业只生成一次
ALTER TABLE study_plan_items ADD COLUMN assignment_id UUID;
CREATE INDEX IF NOT EXISTS idx_plan_items_assignment
    ON study_plan_items (assignment_id, owner_id);
