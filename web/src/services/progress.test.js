import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildWeekly, loadWeeklyReport, shareText, weekLabel } from './progress';

describe('buildWeekly', () => {
  it('整理维度与 before/after 差值', () => {
    const report = buildWeekly({
      weekStart: '2026-10-12',
      weekEnd: '2026-10-18',
      planTotal: 10,
      planCompleted: 6,
      completionRate: 60,
      sessions: 3,
      answers: 9,
      averageScore: 72.35,
      scoreDelta: 4.2,
      dimensions: { relevance: 80, logic: 60 },
      suggestions: ['逻辑：先给结论'],
      bestImprovement: { beforeScore: 55, afterScore: 70 },
    });

    expect(report.completionRate).toBe(60);
    expect(report.deltaLabel).toBe('+4.2 分');
    expect(report.dimensions).toEqual([
      { key: 'relevance', label: '内容相关性', value: 80 },
      { key: 'logic', label: '回答逻辑', value: 60 },
    ]);
    expect(report.bestImprovement.delta).toBe(15);
  });

  it('没有可对比数据时给出明确文案', () => {
    const report = buildWeekly({ scoreDelta: null, dimensions: {}, completionRate: 0 });
    expect(report.deltaLabel).toBe('暂无可对比数据');
  });
});

describe('分享与日期', () => {
  it('分享文案只包含统计，不包含题干/转写', () => {
    const text = shareText({ planCompleted: 6, planTotal: 10, sessions: 3, averageScore: 72.35 });
    expect(text).toContain('6/10');
    expect(text).not.toContain('题干');
  });

  it('周起始日期格式化成中文短日期', () => {
    expect(weekLabel('2026-10-12')).toContain('10月');
  });
});

describe('周报接口', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('带 week 参数时拼查询串', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    await loadWeeklyReport('2026-10-12');
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/progress/weekly?week=2026-10-12');
  });
});
