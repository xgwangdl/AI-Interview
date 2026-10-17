-- 求职目标与每日训练计划（Phase 1）。
--
-- 目标不是"多一个配置项"，而是所有练习的锚点：首页知道距面试几天、今天该练什么，
-- 练习记录也能回答"这是为哪个岗位练的"。
CREATE TABLE IF NOT EXISTS job_goals (
    id              UUID PRIMARY KEY,
    owner_id        VARCHAR(160) NOT NULL,
    company         VARCHAR(200),
    role            VARCHAR(160) NOT NULL,
    job_description TEXT,
    resume_id       UUID,
    stage           VARCHAR(32)  NOT NULL,   -- oa / phone / technical / system_design / hr / final
    interview_at    TIMESTAMPTZ,
    status          VARCHAR(16)  NOT NULL,   -- preparing / applied / interviewing / done / archived
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    archived_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_job_goals_owner
    ON job_goals (owner_id, status, interview_at);

CREATE TABLE IF NOT EXISTS study_plans (
    id         UUID PRIMARY KEY,
    goal_id    UUID REFERENCES job_goals(id) ON DELETE CASCADE,
    owner_id   VARCHAR(160) NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    status     VARCHAR(16)  NOT NULL,        -- active / completed / archived
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_study_plans_owner
    ON study_plans (owner_id, status, start_date, end_date);

CREATE TABLE IF NOT EXISTS study_plan_items (
    id                UUID PRIMARY KEY,
    plan_id           UUID         NOT NULL REFERENCES study_plans(id) ON DELETE CASCADE,
    owner_id          VARCHAR(160) NOT NULL,
    plan_date         DATE         NOT NULL,
    kind              VARCHAR(24)  NOT NULL, -- weak_dimension / frequent / mistake / english / assignment
    session_id        UUID,
    question_ref      VARCHAR(120),
    title             VARCHAR(200) NOT NULL,
    estimated_minutes INTEGER      NOT NULL DEFAULT 5,
    status            VARCHAR(16)  NOT NULL, -- pending / in_progress / completed / skipped
    position          INTEGER      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT uq_plan_item_position UNIQUE (plan_id, plan_date, position)
);

CREATE INDEX IF NOT EXISTS idx_plan_items_owner_date
    ON study_plan_items (owner_id, plan_date, status);
CREATE INDEX IF NOT EXISTS idx_plan_items_session
    ON study_plan_items (session_id);

-- 练习与目标的关联：为空表示"快速练习"，不进目标进度与每日计划。
ALTER TABLE practice_sessions ADD COLUMN goal_id UUID;
CREATE INDEX IF NOT EXISTS idx_practice_sessions_goal
    ON practice_sessions (goal_id, created_at DESC);
