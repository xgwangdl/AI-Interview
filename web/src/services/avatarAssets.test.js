import { describe, expect, it } from 'vitest';
import { AVATAR_VARIANTS, frameSrc } from './avatarAssets';

describe('avatarAssets', () => {
  it('默认仍使用面试官素材，避免影响其他页面', () => {
    expect(frameSrc('mouth-a')).toBe('/assets/avatar/mouth-a.png');
    expect(frameSrc('mouth-a', AVATAR_VARIANTS.INTERVIEWER)).toBe('/assets/avatar/mouth-a.png');
  });

  it('英语口语使用独立的女性外教素材集', () => {
    expect(frameSrc('mouth-a', AVATAR_VARIANTS.ENGLISH_COACH))
      .toBe('/assets/avatar/english-coach/mouth-a.png?v=20261027b');
  });

  it('未知素材集安全回退到面试官，不拼出任意路径', () => {
    expect(frameSrc('mouth-o', '../unknown')).toBe('/assets/avatar/mouth-o.png');
  });
});
