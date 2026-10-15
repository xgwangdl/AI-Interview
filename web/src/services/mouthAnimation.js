/**
 * 嘴型选择逻辑（纯函数，便于单测）。
 *
 * 说明清楚这层做到什么程度：这是**按音量分档挑嘴型**的简单动画，
 * 不是音素级口型同步——它只知道"声音大不大"，不知道在念什么字。
 */
import { TALK_ROTATION } from './avatarAssets';

/** 短于这个长度的"句子"（"嗯。""好。"）并进上一句，避免一个字单独弹一行。 */
const MIN_SENTENCE_CHARS = 4;

/** 各档位对应的基准嘴型（0=闭唇 … 3=较大张嘴）。 */
export const LEVEL_FRAMES = ['mouth-m', 'mouth-e', 'mouth-normal', 'mouth-a'];

/**
 * 音量 → 张嘴档位。
 * 阈值偏保守：宁可少张一点，也不要轻微底噪就把嘴张很大。
 */
export function levelFromAmplitude(value) {
  if (!Number.isFinite(value) || value < 0) return 0;
  if (value < 0.06) return 0;
  if (value < 0.18) return 1;
  if (value < 0.42) return 2;
  return 3;
}

/**
 * 选一帧。`rotation` 递增用于在同一档位内换花样，
 * 否则说话时会在两张图之间来回跳，看着很假。
 */
export function frameForLevel(level, rotation = 0) {
  if (level <= 0) return 'mouth-m';
  const base = LEVEL_FRAMES[Math.min(level, LEVEL_FRAMES.length - 1)];
  // 奇数轮次穿插循环表里的帧，偶数轮次用档位基准帧
  if (rotation % 2 === 0) return base;
  return TALK_ROTATION[rotation % TALK_ROTATION.length];
}

/** 没有音量分析器时的降级：按固定节奏在循环表里推进。 */
export function nextFallbackFrame(index) {
  return TALK_ROTATION[index % TALK_ROTATION.length];
}

/**
 * 把一段示范回答切成"字幕句"。
 *
 * 按中文/英文的句末标点切，标点留在前一句末尾；过短的残句并进上一句，
 * 否则一个字也会单独弹出一行，看起来像卡顿。
 */
export function splitSentences(text) {
  if (!text) return [];
  const parts = (text.match(/[^。！？!?；;\n]+[。！？!?；;]?/g) || [text])
    .map((part) => part.trim())
    .filter(Boolean);
  const merged = [];
  for (const part of parts) {
    const previous = merged[merged.length - 1];
    // 判的是**当前这句**太短（例如"嗯。""好。"），把它并进上一句，
    // 而不是判上一句短 —— 否则连续几个短句会被并成一整段。
    if (previous && part.length < MIN_SENTENCE_CHARS) {
      merged[merged.length - 1] = previous + part;
    } else {
      merged.push(part);
    }
  }
  return merged;
}

/**
 * 按播放进度决定"已经念到第几句"。
 *
 * 用字符数占比近似时间占比：中文 TTS 里两者相关性够高，且不需要词级时间戳。
 * 至少显示第一句，避免开头一段时间字幕是空的。
 */
export function revealedSentenceCount(sentences, progress) {
  if (!sentences.length) return 0;
  if (!Number.isFinite(progress) || progress < 0) return sentences.length;  // 没在播：整段显示
  if (progress >= 0.999) return sentences.length;
  const total = sentences.reduce((sum, item) => sum + item.length, 0);
  const target = total * progress;
  let cumulative = 0;
  for (let index = 0; index < sentences.length; index += 1) {
    cumulative += sentences[index].length;
    if (cumulative >= target) return index + 1;
  }
  return sentences.length;
}

/**
 * 音量平滑：起音快、落音慢。
 *
 * 直接用瞬时 RMS 会让嘴型在阈值附近来回跳（听感上是"抖"）。这里做一阶平滑：
 * 音量上升时跟得快（张嘴要及时），下降时跟得慢（闭嘴要有个收的过程），
 * 这也是人说话时的自然节奏。
 */
export const ATTACK = 0.55;
export const RELEASE = 0.16;

export function smoothAmplitude(previous, target, { attack = ATTACK, release = RELEASE } = {}) {
  if (!Number.isFinite(target) || target < 0) return -1;   // 没有分析器
  if (previous === null || previous === undefined || previous < 0) return target;
  const k = target > previous ? attack : release;
  return previous + (target - previous) * k;
}

/** 分档阈值（与 levelFromAmplitude 保持一致）。 */
export const LEVEL_THRESHOLDS = [0.06, 0.18, 0.42];

/**
 * 带迟滞的分档：向上要明显超过阈值才升档，向下要明显低于阈值才降档。
 * 没有迟滞时，音量停在 0.18 附近会让嘴型在"小幅"和"正常"之间每秒抖好几次。
 */
export const HYSTERESIS = 0.3;

export function levelWithHysteresis(previousLevel, value, margin = HYSTERESIS) {
  if (!Number.isFinite(value) || value < 0) return 0;
  let level = Math.max(0, Math.min(LEVEL_THRESHOLDS.length, previousLevel ?? 0));
  // 升档：超过阈值上浮 margin 才认
  while (level < LEVEL_THRESHOLDS.length && value > LEVEL_THRESHOLDS[level] * (1 + margin)) {
    level += 1;
  }
  // 降档：跌破阈值下浮 margin 才认
  while (level > 0 && value < LEVEL_THRESHOLDS[level - 1] * (1 - margin)) {
    level -= 1;
  }
  return level;
}
