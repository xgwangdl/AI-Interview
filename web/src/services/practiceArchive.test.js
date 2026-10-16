import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildArchive, loadArchive, trendPoints } from './practiceArchive';

function summary(overrides = {}) {
  return {
    scopeNote: '只统计还没过期的已完成练习',
    windowSize: 5,
    totals: { scoredSessions: 3, evaluatedAnswers: 9 },
    enoughForTrend: true,
    trend: [
      { sessionId: 'a', role: 'Java 后端', createdAt: '2026-10-10T02:00:00Z', overall: 61.5, dimensions: {} },
      { sessionId: 'b', role: 'Java 后端', createdAt: '2026-10-12T02:00:00Z', overall: 70, dimensions: {} },
      { sessionId: 'c', role: 'Java 后端', createdAt: '2026-10-14T02:00:00Z', overall: 82.25, dimensions: {} },
    ],
    dimensions: [
      { key: 'relevance', average: 86.666, samples: 3 },
      { key: 'starStructure', average: 58, samples: 3 },
    ],
    weakest: [{ key: 'starStructure', average: 58, samples: 3 }],
    ...overrides,
  };
}

describe('buildArchive', () => {
  it('把后端的 key 翻成中文标签，维度与短板各自成型', () => {
    const archive = buildArchive(summary());

    expect(archive.dimensions).toEqual([
      { key: 'relevance', label: '内容相关性', average: 86.7, samples: 3 },
      { key: 'starStructure', label: 'STAR 结构', average: 58, samples: 3 },
    ]);
    expect(archive.weakest[0].label).toBe('STAR 结构');
    expect(archive.weakest[0].advice).toContain('Situation');
    expect(archive.focusKeys).toEqual(['starStructure']);
    expect(archive.latestRole).toBe('Java 后端');
    expect(archive.scoredSessions).toBe(3);
    expect(archive.evaluatedAnswers).toBe(9);
  });

  it('只练过一场时不装作有趋势', () => {
    const archive = buildArchive(summary({
      enoughForTrend: false,
      trend: [{ sessionId: 'a', role: 'Java 后端', createdAt: '2026-10-10T02:00:00Z', overall: 61.5 }],
      weakest: [],
    }));

    expect(archive.needsMoreSessions).toBe(true);
    expect(archive.focusKeys).toEqual([]);
    expect(archive.weakest).toEqual([]);
  });

  it('空档案不炸：没有练习时就返回空壳', () => {
    const archive = buildArchive({});

    expect(archive.trend).toEqual([]);
    expect(archive.dimensions).toEqual([]);
    expect(archive.weakest).toEqual([]);
    expect(archive.scoredSessions).toBe(0);
    expect(archive.needsMoreSessions).toBe(true);
  });

  it('没分数的点也留在趋势里，但分数记成 null 而不是 0', () => {
    const archive = buildArchive(summary({
      trend: [{ sessionId: 'a', role: 'Java 后端', createdAt: '2026-10-10T02:00:00Z', overall: null }],
      enoughForTrend: false,
    }));

    expect(archive.trend[0].overall).toBeNull();
  });
});

describe('trendPoints', () => {
  it('分数映射成画布坐标，高分在上（y 更小）', () => {
    const archive = buildArchive(summary());
    const points = trendPoints(archive.trend, 300, 120);

    expect(points).toHaveLength(3);
    expect(points[0].x).toBeLessThan(points[2].x);
    expect(points[2].y).toBeLessThan(points[0].y);
  });

  it('只有一个点时画在中间，而不是挤在最左边', () => {
    const points = trendPoints([{ overall: 70, label: '10/10' }], 300, 120);

    expect(points).toHaveLength(1);
    expect(points[0].x).toBe(150);
  });

  it('没有数据就没有点（由界面决定显示空状态）', () => {
    expect(trendPoints([], 300, 120)).toEqual([]);
    expect(trendPoints(undefined, 300, 120)).toEqual([]);
  });
});

describe('loadArchive', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('按 limit 拉取训练档案', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await loadArchive(8);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/practice-archive?limit=8');
  });
});
