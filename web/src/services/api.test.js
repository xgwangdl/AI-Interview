import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, getAnswerMedia } from './api';

function fakeResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
    json: async () => JSON.parse(body),
    blob: async () => new Blob(['video']),
  };
}

describe('apiRequest', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('取出后端统一错误体里的 message，而不是把整段 JSON 当错误消息', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      fakeResponse(404, JSON.stringify({ code: 404, message: 'Practice session not found', data: null })),
    );

    await expect(apiRequest('/practice-sessions/x')).rejects.toMatchObject({
      message: 'Practice session not found',
      status: 404,
    });
  });

  it('非 JSON 的错误响应保留原文', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(502, 'Bad Gateway'));

    await expect(apiRequest('/x')).rejects.toMatchObject({ message: 'Bad Gateway', status: 502 });
  });

  it('204 返回 null 而不是尝试解析 JSON', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(204, ''));

    await expect(apiRequest('/x', { method: 'DELETE' })).resolves.toBeNull();
  });

  it('带上 Bearer token', async () => {
    localStorage.setItem('token', 'tok-1');
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, '{}'));
    globalThis.fetch = fetchMock;

    await apiRequest('/x');

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok-1');
  });

  it('FormData 请求不设置 Content-Type，交给浏览器补 boundary', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, '{}'));
    globalThis.fetch = fetchMock;

    await apiRequest('/x', { method: 'POST', body: new FormData() });

    expect(fetchMock.mock.calls[0][1].headers['Content-Type']).toBeUndefined();
  });
});

describe('getAnswerMedia', () => {
  it('读取失败时抛出可读的消息', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: false, status: 404 });

    await expect(getAnswerMedia('a-1')).rejects.toThrow('无法读取这段视频');
  });
});
