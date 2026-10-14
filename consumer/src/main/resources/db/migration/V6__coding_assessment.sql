-- 笔试模块（独立于视频面试）
--
-- 题目与提交分开存：题目由 AI 生成（带参考解与测试用例）后落库，
-- 每道题的实体都挂在一次 coding_session 下，便于整场查看与按保留期清理。

CREATE TABLE IF NOT EXISTS coding_sessions (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    company VARCHAR(255),
    job_description TEXT,
    difficulty VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    question_ids_json TEXT NOT NULL,
    retention_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_coding_sessions_owner
    ON coding_sessions (owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_coding_sessions_retention
    ON coding_sessions (retention_until);

-- 一道题就是一行；五种题型共用这张表，靠 type 区分，各题型只填自己用到的列。
--   coding       上机编程：language + starter_code + test_cases_json + answer_json(参考解)
--   sql          数据库题：sql_setup + answer_json(参考查询) + test_cases_json(期望结果)
--   fill_blank   编程填空：starter_code(带占位符) + blanks_json + answer_json(每空候选答案)
--   choice       选择题：options_json + answer_json(正确选项)
--   short_answer 简答题：answer_json(参考要点)，判分交给模型
CREATE TABLE IF NOT EXISTS code_questions (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES coding_sessions(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    type VARCHAR(32) NOT NULL,
    language VARCHAR(32),
    title VARCHAR(255) NOT NULL,
    statement TEXT NOT NULL,
    starter_code TEXT,
    blanks_json TEXT,
    options_json TEXT,
    answer_json TEXT,
    test_cases_json TEXT,
    sql_setup TEXT,
    difficulty VARCHAR(32),
    tags VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_code_questions_session_position UNIQUE (session_id, position)
);

CREATE TABLE IF NOT EXISTS code_submissions (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES coding_sessions(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES code_questions(id) ON DELETE CASCADE,
    owner_id VARCHAR(255) NOT NULL,
    answer_text TEXT,
    status VARCHAR(32) NOT NULL,
    passed_cases INTEGER NOT NULL DEFAULT 0,
    total_cases INTEGER NOT NULL DEFAULT 0,
    runtime_ms BIGINT,
    stdout TEXT,
    stderr TEXT,
    compile_output TEXT,
    case_results_json TEXT,
    ai_review_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_code_submissions_question
    ON code_submissions (question_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_code_submissions_session
    ON code_submissions (session_id);
