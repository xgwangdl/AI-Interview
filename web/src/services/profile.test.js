import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  changePassword, formatJoinedAt, loadProfile, normalizeFontScale, profileDisplayName,
  profileInitial, rememberDisplayName, saveProfile,
} from './profile';

function fakeResponse(body) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
  };
}

describe('profile helpers', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('昵称优先于登录名并提供兜底', () => {
    expect(profileDisplayName({ loginName: 'alice', displayName: '青禾' })).toBe('青禾');
    expect(profileDisplayName({ loginName: 'alice', displayName: '  ' })).toBe('alice');
    expect(profileDisplayName(null)).toBe('练习者');
  });

  it('只接受支持的字号值并安全回退', () => {
    expect(normalizeFontScale('large')).toBe('large');
    expect(normalizeFontScale('xlarge')).toBe('xlarge');
    expect(normalizeFontScale('huge')).toBe('standard');
    expect(normalizeFontScale(undefined)).toBe('standard');
  });

  it('头像首字母和注册日期有稳定输出', () => {
    expect(profileInitial(' 青禾 ')).toBe('青');
    expect(formatJoinedAt('2026-10-17T08:00:00Z')).toContain('2026');
    expect(formatJoinedAt('bad-date')).toBe('—');
  });

  it('昵称缓存不会覆盖作为数据归属键的 userName', () => {
    localStorage.setItem('userName', 'alice');
    rememberDisplayName('青禾');
    expect(localStorage.getItem('userName')).toBe('alice');
    expect(localStorage.getItem('light-interview-display-name')).toBe('青禾');
  });
});

describe('profile API', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('读取与保存使用个人资料接口', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse({ loginName: 'alice' }));

    await loadProfile();
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/me/profile');

    await saveProfile({ displayName: '青禾' });
    expect(globalThis.fetch.mock.calls[1][0]).toBe('/api/v1/me/profile');
    expect(globalThis.fetch.mock.calls[1][1].method).toBe('PUT');
  });

  it('修改密码使用密码接口', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse({ changed: true }));

    await changePassword({ currentPassword: 'old-secret', newPassword: 'new-secret' });

    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/me/password');
    expect(globalThis.fetch.mock.calls[0][1].method).toBe('PUT');
  });
});
