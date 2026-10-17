-- 个人设置：昵称、时区、界面字号、减弱动效与注册时间。
-- candidate_name 是历史数据归属键，继续作为只读登录名，不随个人资料修改。
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS display_name VARCHAR(40);
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS font_scale VARCHAR(16) NOT NULL DEFAULT 'standard';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS reduce_motion BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
