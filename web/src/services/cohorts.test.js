import { beforeEach, describe, expect, it, vi } from 'vitest';
import { completionPercent, createAssignment, dueLabel, joinCohort, roleLabel } from './cohorts';

describe('小组辅助函数', () => {
  it('完成率按个人完成/总数计算', () => {
    expect(completionPercent({ myCompleted: 3, myTotal: 10 })).toBe(30);
    expect(completionPercent({ myCompleted: 0, myTotal: 0 })).toBe(0);
  });

  it('角色和截止时间文案', () => {
    expect(roleLabel('coach')).toBe('助教');
    expect(roleLabel('learner')).toBe('学员');
    expect(dueLabel('2026-10-20T10:00:00Z')).toContain('10月');
    expect(dueLabel(null)).toBe('未设截止时间');
  });
});

describe('小组接口', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('加入小组传邀请码', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;
    await joinCohort('ABC12345');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/cohorts/join');
    expect(fetchMock.mock.calls[0][1].body).toContain('ABC12345');
  });

  it('布置作业带天数/每天题数/截止时间', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 201, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;
    await createAssignment('c-1', { title: '5 天训练', days: 5, itemsPerDay: 3, dueAt: null });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/cohorts/c-1/assignments');
    expect(fetchMock.mock.calls[0][1].body).toContain('itemsPerDay');
  });
});
