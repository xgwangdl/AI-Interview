-- 错题本：把"题目本身"留在成绩档案里。
--
-- 背景：V9 的档案只存分数与维度，场次 7 天后连 questions_json 一起被清掉，
-- 于是"把某道题重练一遍"根本没有题面可用 —— 分数记得住，题记不住。
-- 这里补上题面与题目类型（追问题目靠 category 区分，不进错题本），
-- 顺带把面试类型与面试官风格也存一份，重练时能沿用原场次的设置。
ALTER TABLE practice_score_archive ADD COLUMN question_text TEXT;
ALTER TABLE practice_score_archive ADD COLUMN question_category VARCHAR(100);
ALTER TABLE practice_score_archive ADD COLUMN interview_type VARCHAR(32);
ALTER TABLE practice_score_archive ADD COLUMN interviewer_style VARCHAR(32);

-- 回填：场次还在保留期内的，从 questions_json 里按 question_id 把题面补出来。
-- 晚于保留期被清理的场次补不回来（题面已经不存在了），那部分只能从这次升级之后开始积累。
UPDATE practice_score_archive a
SET question_text = item.value ->> 'text',
    question_category = item.value ->> 'category'
FROM practice_sessions s,
     LATERAL jsonb_array_elements(s.questions_json::jsonb) AS item
WHERE s.id = a.session_id
  AND item.value ->> 'id' = a.question_id
  AND a.question_text IS NULL;

UPDATE practice_score_archive a
SET interview_type = s.interview_type,
    interviewer_style = s.interviewer_style
FROM practice_sessions s
WHERE s.id = a.session_id
  AND a.interview_type IS NULL;

-- 错题本要按 owner + 题目分组统计（同一道题重练几次，分有没有上去）
CREATE INDEX idx_score_archive_owner_question ON practice_score_archive (owner_id, question_id);
