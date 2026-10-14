-- 每个用户自己的页面主题色（green / blue / amber）。
-- 放在 candidates 上而不是单独建表：偏好目前只有这一个字段，
-- 等以后偏好变多（语言、通知等）再抽独立的 preferences 表。
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS theme VARCHAR(32);
