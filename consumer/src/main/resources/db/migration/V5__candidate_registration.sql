-- 开放注册（issue：用户注册）
--
-- 1) 用户名唯一由数据库保证。V3 只建了 (candidate_name, invitation_code) 的非唯一索引，
--    而实体上却写着 unique = true —— 两边不一致，注册功能必须先把这个洞补上，
--    否则同名账号可以无限创建，登录时取到哪一条全看运气。
CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_name
    ON candidates (candidate_name);

-- 2) invitation_code 从「一次性邀请码」变成「用户自设密码」，存的是 BCrypt 哈希。
--    这里不再要求它唯一：不同用户完全可能选到同一个密码，哈希后也会相同。
ALTER TABLE candidates DROP CONSTRAINT IF EXISTS candidates_invitation_code_key;
DROP INDEX IF EXISTS uq_candidates_code;

-- 3) 已有账号（例如手工插入的 smoke/123456）保持原样：明文口令仍可登录，
--    登录成功后由后端改写为 BCrypt 哈希，无需人工迁移。
