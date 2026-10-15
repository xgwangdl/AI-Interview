import { describe, expect, it } from 'vitest';
import {
  ATTACK, RELEASE, frameForLevel, levelFromAmplitude, levelWithHysteresis,
  nextFallbackFrame, smoothAmplitude,
} from './mouthAnimation';
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

describe('smoothAmplitude', () => {
  it('没有分析器时返回 -1，调用方据此走降级', () => {
    expect(smoothAmplitude(0.5, -1)).toBe(-1);
    expect(smoothAmplitude(0.5, NaN)).toBe(-1);
  });

  it('首个样本直接采用，不从上一次的残留值爬上来', () => {
    expect(smoothAmplitude(null, 0.4)).toBe(0.4);
  });

  it('上升比下降快：张嘴及时，闭嘴有个收的过程', () => {
    const rising = smoothAmplitude(0.1, 0.5);
    const falling = smoothAmplitude(0.5, 0.1);
    expect(rising - 0.1).toBeGreaterThan(0.5 - falling);
    expect(ATTACK).toBeGreaterThan(RELEASE);
  });
});

describe('levelWithHysteresis', () => {
  it('阈值附近来回抖动时不会反复切换档位', () => {
    // 0.18 是 1→2 档的边界；在它上下小幅波动不应该跳档
    let level = levelWithHysteresis(1, 0.19);
    expect(level).toBe(1);
    for (const value of [0.20, 0.17, 0.19, 0.16, 0.20]) {
      level = levelWithHysteresis(level, value);
      expect(level).toBe(1);
    }
  });

  it('明显超过阈值才升档，明显低于才降档', () => {
    expect(levelWithHysteresis(1, 0.30)).toBe(2);   // 远超 0.18
    expect(levelWithHysteresis(2, 0.10)).toBe(1);   // 远低于 0.18*0.7=0.126
    expect(levelWithHysteresis(2, 0.15)).toBe(2);   // 只是略低，维持
  });

  it('静音回到闭唇档', () => {
    expect(levelWithHysteresis(3, 0)).toBe(0);
    expect(levelWithHysteresis(2, -1)).toBe(0);
  });

  it('档位不会越界', () => {
    expect(levelWithHysteresis(3, 10)).toBe(3);
    expect(levelWithHysteresis(0, 0)).toBe(0);
  });
});
