import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildUsage, loadModelUsage } from './modelUsage';

function usage(overrides = {}) {
  return {
    enabled: true,
    date: '2026-10-16',
    dailyLimit: 20,
    used: 2,
    remaining: 18,
    resetsAt: '2026-10-16T16:00:00Z',
    concurrentLimit: 3,
    inFlight: 0,
    note: '出题、追问、评分都会计入',
    ...overrides,
  };
}

describe('buildUsage', () => {
  it('显示剩余额度与重置时间', () => {
    const result = buildUsage(usage());

    expect(result.enabled).toBe(true);
    expect(result.label).toBe('今日 AI 额度 18/20');
    expect(result.remaining).toBe(18);
    expect(result.exhausted).toBe(false);
    expect(result.tight).toBe(false);
    expect(result.resetLabel).toMatch(/重置$/);
  });

  it('快用完时标成紧张（顶栏要能提前提醒，而不是等被拦住）', () => {
    expect(buildUsage(usage({ used: 18, remaining: 2 })).tight).toBe(true);
    expect(buildUsage(usage({ used: 0, remaining: 20 })).tight).toBe(false);
  });

  it('用完时标成 exhausted，但页面照常渲染（不弹窗、不报错）', () => {
    const result = buildUsage(usage({ used: 20, remaining: 0 }));

    expect(result.exhausted).toBe(true);
    expect(result.label).toContain('0/20');
  });

  it('护栏关掉、或者没登录拿不到数据时不显示这一块', () => {
    expect(buildUsage(usage({ enabled: false })).enabled).toBe(false);
    expect(buildUsage(null).enabled).toBe(false);
    expect(buildUsage(null).label).toBe('');
  });

  it('后端没给 remaining 时自己算，别显示 undefined', () => {
    const result = buildUsage(usage({ used: 5, remaining: undefined, resetsAt: null }));

    expect(result.remaining).toBe(15);
    expect(result.resetLabel).toBe('');
  });
});

describe('loadModelUsage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('走 /me/model-usage', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await loadModelUsage();

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/me/model-usage');
  });
});
