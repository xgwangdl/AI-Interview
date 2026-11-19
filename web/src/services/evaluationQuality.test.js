import { describe, expect, it } from 'vitest';
import {
  buildDimensionAssessments,
  displayScore,
  hasEvidenceTiming,
  scoreBandKey,
} from './evaluationQuality';

describe('evaluation quality display', () => {
  it('rounds scores instead of presenting false decimal precision', () => {
    expect(displayScore(82.5)).toBe('83');
    expect(displayScore(null)).toBe('—');
  });

  it('maps scores to stable user-facing bands', () => {
    expect(scoreBandKey(80)).toBe('good');
    expect(scoreBandKey(60)).toBe('fair');
    expect(scoreBandKey(40)).toBe('weak');
    expect(scoreBandKey(39)).toBe('poor');
  });

  it('joins each score with its reason and grounded quote', () => {
    const rows = buildDimensionAssessments({
      scores: { relevance: 82.5, completeness: 64, professionalism: 72, starStructure: 55, logic: 69 },
      dimensionFeedback: [
        { dimension: 'relevance', reason: '直接回答了题目。', evidenceQuote: '我负责订单系统' },
      ],
    });

    expect(rows).toHaveLength(5);
    expect(rows[0]).toMatchObject({
      key: 'relevance',
      score: '83',
      band: 'good',
      reason: '直接回答了题目。',
      evidenceQuote: '我负责订单系统',
    });
    expect(rows[1]).toMatchObject({ key: 'completeness', score: '64', band: 'fair' });
  });

  it('only exposes playback when both evidence timestamps are known', () => {
    expect(hasEvidenceTiming({ startSeconds: 4, endSeconds: 8 })).toBe(true);
    expect(hasEvidenceTiming({ startSeconds: null, endSeconds: null })).toBe(false);
    expect(hasEvidenceTiming({ startSeconds: 4, endSeconds: null })).toBe(false);
  });
});
