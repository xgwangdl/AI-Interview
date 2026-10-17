-- Phase 4：周报、提醒偏好与发送日志。
CREATE TABLE IF NOT EXISTS weekly_reports (
    id           UUID PRIMARY KEY,
    owner_id     VARCHAR(160) NOT NULL,
    week_start   DATE         NOT NULL,
    week_end     DATE         NOT NULL,
    report_json  TEXT         NOT NULL,
    generated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_weekly_report_owner_week UNIQUE (owner_id, week_start)
);

CREATE TABLE IF NOT EXISTS notification_preferences (
    owner_id           VARCHAR(160) PRIMARY KEY,
    email_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    daily_reminder     BOOLEAN      NOT NULL DEFAULT TRUE,
    weekly_report      BOOLEAN      NOT NULL DEFAULT TRUE,
    interview_countdown BOOLEAN     NOT NULL DEFAULT TRUE,
    reminder_time      TIME         NOT NULL DEFAULT '20:00',
    timezone           VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_log (
    id         UUID PRIMARY KEY,
    owner_id   VARCHAR(160) NOT NULL,
    kind       VARCHAR(32)  NOT NULL,  -- daily / weekly / countdown
    channel    VARCHAR(16)  NOT NULL,  -- email / in_app
    sent_at    TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(16)  NOT NULL,  -- sent / skipped / failed
    error      VARCHAR(300)
);

CREATE INDEX IF NOT EXISTS idx_notification_log_owner
    ON notification_log (owner_id, kind, sent_at DESC);
