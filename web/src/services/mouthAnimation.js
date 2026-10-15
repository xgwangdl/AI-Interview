/**
 * 嘴型选择逻辑（纯函数，便于单测）。
 *
 * 说明清楚这层做到什么程度：这是**按音量分档挑嘴型**的简单动画，
 * 不是音素级口型同步——它只知道"声音大不大"，不知道在念什么字。
 */
import { TALK_ROTATION } from './avatarAssets';

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
