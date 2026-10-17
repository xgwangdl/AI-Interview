-- 外语口语练习（英语 / 日语）：一次会话 = 开场白 + 若干轮"你说一句、AI 回一句"。
--
-- 为什么不复用 practice_sessions：那边的结构是"一道题一段视频 + 异步评分"，
-- 而对话是"一轮一段音频 + 同步回复"，硬塞进去会多出一堆永远为空的列，报告口径也会互相污染。
-- 复用的部分是鉴权、额度、媒体存储与数字人组件，不是表结构。
CREATE TABLE IF NOT EXISTS language_sessions (
    id              UUID PRIMARY KEY,
    owner_id        VARCHAR(160) NOT NULL,
    language        VARCHAR(8)   NOT NULL,      -- en / ja
    scenario        VARCHAR(32)  NOT NULL,      -- interview / standup / daily
    level           VARCHAR(16)  NOT NULL,      -- beginner / intermediate / advanced
    target          VARCHAR(200),
    status          VARCHAR(16)  NOT NULL,      -- active / finished / abandoned
    turn_count      INTEGER      NOT NULL DEFAULT 0,
    max_turns       INTEGER      NOT NULL,
    report_json     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    finished_at     TIMESTAMPTZ,
    retention_until TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_language_sessions_owner
    ON language_sessions (owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_language_sessions_retention
    ON language_sessions (retention_until);

CREATE TABLE IF NOT EXISTS language_turns (
    id                 UUID PRIMARY KEY,
    session_id         UUID         NOT NULL REFERENCES language_sessions(id) ON DELETE CASCADE,
    turn_index         INTEGER      NOT NULL,
    audio_key          VARCHAR(100),            -- 用户这一轮的录音
    audio_content_type VARCHAR(100),
    audio_ms           INTEGER,
    reply_audio_key    VARCHAR(100),            -- AI 的语音（日语暂为 null：本机/浏览器念）
    transcript         TEXT,
    reply_text         TEXT,
    feedback_json      TEXT,
    asr_ms             INTEGER,
    llm_ms             INTEGER,
    tts_ms             INTEGER,
    total_ms           INTEGER,
    status             VARCHAR(16)  NOT NULL,   -- answered / skipped
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_language_turns_session_index UNIQUE (session_id, turn_index)
);

CREATE INDEX IF NOT EXISTS idx_language_turns_session
    ON language_turns (session_id, turn_index);
