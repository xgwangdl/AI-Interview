-- 登录所需的最小身份表。
--
-- /login 通过「姓名 + 邀请码」在 candidates 表中查找用户。该表此前只存在于 ddl/ 下手动
-- 执行的脚本里，没有纳入 Flyway，因此全新数据库上登录会直接报「表不存在」。
-- 这里只建登录链路真正依赖的表；其余遗留表（resumes、knowledge、resume_vectors）属于
-- PLAN 中待移除的旧功能，暂不纳入迁移管理。

CREATE SEQUENCE IF NOT EXISTS interviewer_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

CREATE TABLE IF NOT EXISTS candidates (
    id BIGINT NOT NULL DEFAULT nextval('interviewer_id_seq'),
    candidate_name VARCHAR(255) NOT NULL,
    invitation_code VARCHAR(255) NOT NULL,
    cv TEXT,
    email VARCHAR(255),
    birth DATE,
    status INTEGER NOT NULL DEFAULT 1,
    picture_url VARCHAR(255),
    CONSTRAINT interviewer_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_candidates_name_code
    ON candidates (candidate_name, invitation_code);
