import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getTodayPlan, goalProgressPercent, interviewLabel, startPlanItem, toIsoDateTime, toLocalDateTime,
} from './goals';

describe('目标与计划文案', () => {
  it('距面试天数覆盖未来/当天/过去/未设置', () => {
    expect(interviewLabel(3)).toBe('距面试 3 天');
    expect(interviewLabel(0)).toBe('今天面试');
    expect(interviewLabel(-2)).toBe('已过 2 天');
    expect(interviewLabel(null)).toBe('未设面试日期');
  });

  it('计划进度按完成/总数算，没计划时为 0', () => {
    expect(goalProgressPercent({ planItems: 12, planCompleted: 3 })).toBe(25);
    expect(goalProgressPercent({ planItems: 0, planCompleted: 0 })).toBe(0);
    expect(goalProgressPercent(null)).toBe(0);
  });

  it('datetime-local 与 ISO 互转，空值保持 null', () => {
    const iso = toIsoDateTime('2026-10-20T14:30');
    expect(iso).toMatch(/^2026-10-20T/);
    expect(toLocalDateTime(iso)).toBe('2026-10-20T14:30');
    expect(toIsoDateTime('')).toBeNull();
  });
});

describe('目标接口参数', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('今日计划带 goalId 时拼查询串', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await getTodayPlan('goal-1');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/plans/today?goalId=goal-1');
  });

  it('开始计划项走 POST', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await startPlanItem('item-1');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/plans/items/item-1/start');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });
});
