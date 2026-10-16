-- 分数档案：把"成绩与维度"和"录像/转写"分开保留。
--
-- 动机（见 docs/PRACTICE-ARCHIVE-REQUIREMENTS.md §2.2）：练习默认 7 天后连场次、回答、报告一起删，
-- 于是训练档案永远只有 7 天视野。现在起，评分结果单独存一份（只有分数，不含转写与录像），
-- 场次过期后档案仍然能看趋势；用户主动删除某场练习时，这一份也一起删。
CREATE TABLE practice_score_archive (
    id                 UUID PRIMARY KEY,
    owner_id           VARCHAR(160) NOT NULL,
    session_id         UUID         NOT NULL,
    -- 场次可能已经被清理，所以创建时间与岗位在这里各存一份，档案不依赖 practice_sessions
    session_created_at TIMESTAMPTZ  NOT NULL,
    question_index     INTEGER      NOT NULL,
    question_id        VARCHAR(64)  NOT NULL,
    role               VARCHAR(160),
    difficulty         VARCHAR(32),
    overall_score      NUMERIC(6, 2),
    relevance          NUMERIC(6, 2),
    completeness       NUMERIC(6, 2),
    professionalism    NUMERIC(6, 2),
    star_structure     NUMERIC(6, 2),
    logic              NUMERIC(6, 2),
    evaluated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_score_archive_owner ON practice_score_archive (owner_id, session_created_at DESC);
-- 同一场次的同一道题只存一条：评分任务重试时不会写出重复分数
CREATE UNIQUE INDEX idx_score_archive_answer ON practice_score_archive (session_id, question_id);

-- 回填已有评分，免得升级完档案里历史成绩反而没了
INSERT INTO practice_score_archive (id, owner_id, session_id, session_created_at, question_index, question_id,
                                    role, difficulty, overall_score, relevance, completeness, professionalism,
                                    star_structure, logic, evaluated_at)
SELECT gen_random_uuid(),
       s.owner_id,
       s.id,
       s.created_at,
       a.question_index,
       a.question_id,
       s.role,
       s.difficulty,
       (a.evaluation_json::jsonb #>> '{result,overallScore}')::numeric,
       (a.evaluation_json::jsonb #>> '{result,scores,relevance}')::numeric,
       (a.evaluation_json::jsonb #>> '{result,scores,completeness}')::numeric,
       (a.evaluation_json::jsonb #>> '{result,scores,professionalism}')::numeric,
       (a.evaluation_json::jsonb #>> '{result,scores,starStructure}')::numeric,
       (a.evaluation_json::jsonb #>> '{result,scores,logic}')::numeric,
       COALESCE(s.completed_at, s.created_at)
FROM practice_answers a
         JOIN practice_sessions s ON s.id = a.session_id
WHERE a.evaluation_json IS NOT NULL
  AND a.evaluation_json LIKE '{%'
  AND (a.evaluation_json::jsonb #>> '{result,overallScore}') IS NOT NULL
  AND s.status = 'completed';

-- 追问开关：由用户在创建练习时决定，默认关（每道题追问要多一次转写 + 一次模型调用）
ALTER TABLE practice_sessions ADD COLUMN follow_up_enabled BOOLEAN NOT NULL DEFAULT FALSE;
