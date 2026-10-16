import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildUsage, loadModelUsage } from './modelUsage';

function usage(overrides = {}) {
  return {
    enabled: true,
    date: '2026-10-16',
    dailyLimit: 120,
    used: 12,
    remaining: 108,
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
    expect(result.label).toBe('今日 AI 额度 108/120');
    expect(result.remaining).toBe(108);
    expect(result.exhausted).toBe(false);
    expect(result.tight).toBe(false);
    expect(result.resetLabel).toMatch(/重置$/);
  });

  it('快用完时标成紧张（顶栏要能提前提醒，而不是等被拦住）', () => {
    expect(buildUsage(usage({ used: 110, remaining: 10 })).tight).toBe(true);
    expect(buildUsage(usage({ used: 0, remaining: 120 })).tight).toBe(false);
  });

  it('用完时标成 exhausted，但页面照常渲染（不弹窗、不报错）', () => {
    const result = buildUsage(usage({ used: 120, remaining: 0 }));

    expect(result.exhausted).toBe(true);
    expect(result.label).toContain('0/120');
  });

  it('护栏关掉、或者没登录拿不到数据时不显示这一块', () => {
    expect(buildUsage(usage({ enabled: false })).enabled).toBe(false);
    expect(buildUsage(null).enabled).toBe(false);
    expect(buildUsage(null).label).toBe('');
  });

  it('后端没给 remaining 时自己算，别显示 undefined', () => {
    const result = buildUsage(usage({ used: 30, remaining: undefined, resetsAt: null }));

    expect(result.remaining).toBe(90);
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
