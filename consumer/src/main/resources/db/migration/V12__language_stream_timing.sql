-- L2 流式对话：记录"第一段文字 / 第一帧音频"的时间。
-- 流式下总时长几乎不变，用户感知的是首字延迟，所以必须单独把这两个数记下来。
ALTER TABLE language_turns ADD COLUMN llm_first_ms  INTEGER;
ALTER TABLE language_turns ADD COLUMN audio_first_ms INTEGER;
