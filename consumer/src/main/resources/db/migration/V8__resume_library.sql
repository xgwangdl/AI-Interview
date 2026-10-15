-- 简历库（需求文档 docs/RESUME-REQUIREMENTS.md）
--
-- 与遗留的 resumes / resume_vectors 无关：那两张表只存在于 ddl/*.sql、不在 Flyway 管理内，
-- 所以从来没能真正插入过数据。这里重建，并只存"正文 + 版本"，不做结构化分栏。

CREATE TABLE IF NOT EXISTS resumes (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source VARCHAR(32) NOT NULL,           -- upload | manual
    file_name VARCHAR(255),
    raw_text TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_resumes_owner
    ON resumes (owner_id, updated_at DESC);

-- 每次优化一条记录：可回看历史、可逐段采纳。
CREATE TABLE IF NOT EXISTS resume_optimizations (
    id UUID PRIMARY KEY,
    resume_id UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    owner_id VARCHAR(255) NOT NULL,
    role VARCHAR(200),
    job_description TEXT,
    status VARCHAR(32) NOT NULL,           -- completed | failed
    optimized_text TEXT,
    changes_json TEXT,                     -- 逐段：原文/优化后/理由/改动类型
    placeholders_json TEXT,                -- 需要用户补充的位置
    error TEXT,
    model VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_resume_optimizations_resume
    ON resume_optimizations (resume_id, created_at DESC);
