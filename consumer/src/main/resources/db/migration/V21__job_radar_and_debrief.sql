-- Job Radar and interview debrief MVP.
--
-- Design decisions locked before implementation:
-- 1. Agent execution records use job_agent_runs so a future general-purpose
--    agent platform can own the shorter, generic agent_runs name.
-- 2. A lead dedup key includes owner, company, role and normalized JD. The same
--    generic JD can therefore be evaluated for different companies/roles.
-- 3. A running job owns a lease. Expired leases are reclaimed instead of
--    leaving a lead permanently stuck in "evaluating".
-- 4. Debrief raw text is stored only until the user deletes the debrief; the
--    row is then physically deleted and dependent weak points cascade.
-- 5. JSON data is JSONB, but JPA continues to expose it as a JSON string so
--    the existing ObjectMapper-based code style remains unchanged.

CREATE TABLE IF NOT EXISTS job_leads (
    id              uuid PRIMARY KEY,
    owner_id        varchar(80) NOT NULL,
    company         varchar(120),
    role            varchar(120) NOT NULL,
    city            varchar(60),
    salary_min_k    integer,
    salary_max_k    integer,
    jd_text         text NOT NULL,
    jd_hash         char(64) NOT NULL,
    dedup_key       char(64) NOT NULL,
    source          varchar(20) NOT NULL DEFAULT 'manual_paste',
    status          varchar(20) NOT NULL DEFAULT 'new',
    source_url      varchar(500),
    note            varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    archived_at     timestamptz,
    CONSTRAINT ck_job_leads_salary CHECK (
        salary_min_k IS NULL OR salary_max_k IS NULL OR salary_min_k <= salary_max_k
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_leads_owner_dedup
    ON job_leads (owner_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_job_leads_owner_status
    ON job_leads (owner_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS job_evaluations (
    id              uuid PRIMARY KEY,
    owner_id        varchar(80) NOT NULL,
    job_lead_id     uuid NOT NULL REFERENCES job_leads(id) ON DELETE CASCADE,
    jd_hash         char(64) NOT NULL,
    profile_hash    char(64) NOT NULL,
    prompt_version  varchar(20) NOT NULL,
    score           smallint NOT NULL,
    confidence      varchar(10) NOT NULL,
    breakdown_json  jsonb NOT NULL,
    hits_json       jsonb NOT NULL,
    gaps_json       jsonb NOT NULL,
    reason          text,
    recommendation  varchar(20) NOT NULL,
    degraded        boolean NOT NULL DEFAULT false,
    model           varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_evaluations_score CHECK (score BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_eval_dedup
    ON job_evaluations (job_lead_id, jd_hash, profile_hash, prompt_version);
CREATE INDEX IF NOT EXISTS idx_job_eval_lead
    ON job_evaluations (job_lead_id, created_at DESC);

CREATE TABLE IF NOT EXISTS interview_debriefs (
    id                  uuid PRIMARY KEY,
    owner_id            varchar(80) NOT NULL,
    job_lead_id         uuid REFERENCES job_leads(id) ON DELETE SET NULL,
    practice_session_id uuid REFERENCES practice_sessions(id) ON DELETE SET NULL,
    interview_date      date NOT NULL,
    round_name          varchar(30) NOT NULL,
    outcome             varchar(20) NOT NULL DEFAULT 'unknown',
    raw_text            text NOT NULL,
    raw_text_sha256     char(64) NOT NULL,
    raw_text_length     integer NOT NULL,
    privacy_acknowledged_at timestamptz NOT NULL,
    interviewer_focus   varchar(200),
    questions_json      jsonb,
    parse_status        varchar(20) NOT NULL DEFAULT 'pending',
    self_notes          text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_debrief_owner
    ON interview_debriefs (owner_id, interview_date DESC);

CREATE TABLE IF NOT EXISTS training_weak_points (
    id              uuid PRIMARY KEY,
    owner_id        varchar(80) NOT NULL,
    source          varchar(20) NOT NULL,
    debrief_id      uuid REFERENCES interview_debriefs(id) ON DELETE CASCADE,
    topic           varchar(200) NOT NULL,
    dimension_key   varchar(40) NOT NULL,
    evidence        text,
    weight          smallint NOT NULL DEFAULT 3,
    status          varchar(20) NOT NULL DEFAULT 'open',
    closed_at       timestamptz,
    closed_by       varchar(20),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_weak_weight CHECK (weight BETWEEN 1 AND 5),
    CONSTRAINT ck_weak_status CHECK (status IN ('open', 'closed'))
);

CREATE INDEX IF NOT EXISTS idx_weak_owner_status
    ON training_weak_points (owner_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_weak_dimension
    ON training_weak_points (owner_id, dimension_key, status);

CREATE TABLE IF NOT EXISTS job_agent_runs (
    id                uuid PRIMARY KEY,
    owner_id          varchar(80) NOT NULL,
    kind              varchar(40) NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'pending',
    idempotency_key   varchar(160) NOT NULL,
    subject_type      varchar(30),
    subject_id        uuid,
    input_json        jsonb,
    output_json       jsonb,
    steps_json        jsonb,
    error             text,
    attempts          smallint NOT NULL DEFAULT 0,
    next_attempt_at   timestamptz,
    lease_expires_at  timestamptz,
    last_heartbeat_at timestamptz,
    model_calls       smallint NOT NULL DEFAULT 0,
    minutes_saved     smallint NOT NULL DEFAULT 0,
    started_at        timestamptz,
    finished_at       timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_agent_runs_status CHECK (
        status IN ('pending', 'running', 'succeeded', 'failed', 'deferred', 'cancelled')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_agent_runs_idem
    ON job_agent_runs (idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_job_agent_runs_active_subject
    ON job_agent_runs (owner_id, kind, subject_id)
    WHERE status IN ('pending', 'running', 'deferred');
CREATE INDEX IF NOT EXISTS idx_job_agent_runs_due
    ON job_agent_runs (status, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_job_agent_runs_lease
    ON job_agent_runs (status, lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_job_agent_runs_owner_kind
    ON job_agent_runs (owner_id, kind, created_at DESC);

ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS skill_profile_json jsonb;
