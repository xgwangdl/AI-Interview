-- 用户录制隐私同意记录。版本由后端配置控制，客户端不能自行降低版本。
CREATE TABLE IF NOT EXISTS privacy_consents (
    id           UUID         PRIMARY KEY,
    owner_id     VARCHAR(160) NOT NULL REFERENCES candidates(candidate_name) ON DELETE CASCADE,
    consent_type VARCHAR(64)  NOT NULL,
    version      VARCHAR(64)  NOT NULL,
    accepted_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_privacy_consents_owner_type_version UNIQUE (owner_id, consent_type, version)
);

CREATE INDEX IF NOT EXISTS idx_privacy_consents_owner
    ON privacy_consents (owner_id, consent_type, accepted_at DESC);
