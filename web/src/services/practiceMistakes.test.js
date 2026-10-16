import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildMistakes, loadMistakes, retryMistake } from './practiceMistakes';

function book(overrides = {}) {
  return {
    scopeNote: '只收最好一次也没到 75 分的题',
    passThreshold: 75,
    total: 2,
    mistakes: [
      {
        questionId: 'q-1',
        text: '讲一个你优化过的接口',
        category: 'technical',
        role: 'Java 后端工程师',
        difficulty: 'advanced',
        attempts: 2,
        bestScore: 52.4,
        latestScore: 52.4,
        latestAt: '2026-10-15T10:00:00Z',
        weakestDimension: 'starStructure',
        weakestScore: 40,
      },
      {
        questionId: 'q-2',
        attempts: 1,
        bestScore: 70,
        latestScore: 70,
        weakestDimension: 'unknownKey',
      },
    ],
    ...overrides,
  };
}

describe('buildMistakes', () => {
  it('把分数、差距与重练次数翻成页面要的样子', () => {
    const result = buildMistakes(book());

    expect(result.passThreshold).toBe(75);
    expect(result.total).toBe(2);
    expect(result.mistakes[0]).toMatchObject({
      questionId: 'q-1',
      bestScore: 52.4,
      gap: 22.6,
      label: '52.4',
      weakestLabel: 'STAR 结构',
      difficultyLabel: '进阶',
      attemptsLabel: '已重练 1 次',
    });
    expect(result.mistakes[1].attemptsLabel).toBe('还没重练过');
    // 认不出的维度 key 原样显示，而不是变成空白
    expect(result.mistakes[1].weakestLabel).toBe('unknownKey');
    // 没有题干时给一句可读的占位，而不是显示 null
    expect(result.mistakes[1].text).toBe('（这道题没有保存题干）');
  });

  it('空数据不炸：没练过就没有错题', () => {
    const result = buildMistakes({});

    expect(result.mistakes).toEqual([]);
    expect(result.total).toBe(0);
    expect(result.passThreshold).toBe(75);
  });

  it('没有分数时不假装有分数', () => {
    const result = buildMistakes(book({
      mistakes: [{ questionId: 'q-3', text: '老数据', attempts: 1, bestScore: null }],
    }));

    expect(result.mistakes[0].bestScore).toBeNull();
    expect(result.mistakes[0].gap).toBeNull();
    expect(result.mistakes[0].label).toBe('—');
  });
});

describe('接口调用', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('按 limit 拉错题本', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await loadMistakes(3);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/practice-archive/mistakes?limit=3');
  });

  it('重练是对具体那道题发 POST（题 id 要转义）', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await retryMistake('q-0 follow');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/practice-archive/mistakes/q-0%20follow/retry');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });
});
