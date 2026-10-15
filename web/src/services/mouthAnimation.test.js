import { describe, expect, it } from 'vitest';
import { frameForLevel, levelFromAmplitude, nextFallbackFrame } from './mouthAnimation';
import { TALK_ROTATION } from './avatarAssets';

describe('levelFromAmplitude', () => {
  it('静音附近保持闭唇档', () => {
    expect(levelFromAmplitude(0)).toBe(0);
    expect(levelFromAmplitude(0.05)).toBe(0);
  });

  it('按音量分成四档', () => {
    expect(levelFromAmplitude(0.1)).toBe(1);
    expect(levelFromAmplitude(0.3)).toBe(2);
    expect(levelFromAmplitude(0.8)).toBe(3);
  });

  it('没有分析器时（负数）视为闭唇', () => {
    expect(levelFromAmplitude(-1)).toBe(0);
    expect(levelFromAmplitude(NaN)).toBe(0);
  });
});

describe('frameForLevel', () => {
  it('静音一定回到闭唇，不会停在张嘴', () => {
    expect(frameForLevel(0, 0)).toBe('mouth-m');
    expect(frameForLevel(0, 7)).toBe('mouth-m');
  });

  it('音量越大嘴张得越大', () => {
    expect(frameForLevel(1, 0)).toBe('mouth-e');
    expect(frameForLevel(2, 0)).toBe('mouth-normal');
    expect(frameForLevel(3, 0)).toBe('mouth-a');
  });

  it('同一档位内会换帧，不是两张图来回跳', () => {
    const seen = new Set();
    for (let rotation = 0; rotation < 8; rotation += 1) seen.add(frameForLevel(2, rotation));
    expect(seen.size).toBeGreaterThan(1);
    seen.forEach((name) => expect(TALK_ROTATION.includes(name) || name === 'mouth-normal').toBe(true));
  });
});

describe('nextFallbackFrame', () => {
  it('在循环表里循环，不会越界', () => {
    expect(nextFallbackFrame(0)).toBe(TALK_ROTATION[0]);
    expect(nextFallbackFrame(TALK_ROTATION.length)).toBe(TALK_ROTATION[0]);
    expect(TALK_ROTATION.includes(nextFallbackFrame(3))).toBe(true);
  });
});
