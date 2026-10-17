import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiRequest, createAnswerAttempt, generateAnswerRewrite, getAnswerMedia, reEvaluateAnswer,
  saveAnswerRewrite, tryLogin, tryPauseSession, tryRegister, trySyncSession, updateAnswerTranscript,
} from './api';

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

describe('Phase 2 API', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('转写修正走 PATCH', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(200, '{"transcriptText":"fixed"}'));
    await updateAnswerTranscript('a-1', 'fixed');
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/answers/a-1/transcript');
    expect(globalThis.fetch.mock.calls[0][1].method).toBe('PATCH');
  });

  it('重评分与暂停都是 POST', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(202, '{}'));
    await reEvaluateAnswer('a-1');
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/answers/a-1/re-evaluate');

    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(200, '{}'));
    await tryPauseSession('s-1');
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/practice-sessions/s-1/pause');
  });

  it('创建练习会把 continuous 模式带给后端', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(201, '{"id":"s-1"}'));
    await trySyncSession({
      role: 'Java 后端', interviewType: 'technical', difficulty: 'standard', interviewerStyle: 'balanced',
      questionCount: 1, durationMinutes: 4, mode: 'continuous',
      questions: [{ id: 'q-1', text: 'Q', category: 'design' }],
    });
    const body = JSON.parse(globalThis.fetch.mock.calls[0][1].body);
    expect(body.mode).toBe('continuous');
  });

  it('改写与重录接口路径正确', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(200, '{}'));
    await generateAnswerRewrite('a-1');
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/answers/a-1/rewrite');
    expect(globalThis.fetch.mock.calls[0][1].method).toBe('POST');

    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(200, '{}'));
    await saveAnswerRewrite('a-1', 'better');
    expect(globalThis.fetch.mock.calls[0][1].method).toBe('PUT');

    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(201, '{}'));
    await createAnswerAttempt('s-1', 2);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/practice-sessions/s-1/questions/2/attempts');
  });
});

describe('getAnswerMedia', () => {
  it('读取失败时抛出可读的消息', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: false, status: 404 });

    await expect(getAnswerMedia('a-1')).rejects.toThrow('无法读取这段视频');
  });
});

describe('登录与注册', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('登录提交表单编码的 name/code 到 /login，并返回 token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, JSON.stringify({
      code: 200, message: 'ok', data: { token: 'tok', userId: 7, userName: 'alice' },
    })));
    globalThis.fetch = fetchMock;

    const data = await tryLogin('alice', 'secret123');

    expect(data).toEqual({ token: 'tok', userId: 7, userName: 'alice' });
    expect(fetchMock.mock.calls[0][0]).toBe('/login');
    expect(fetchMock.mock.calls[0][1].body).toBeInstanceOf(URLSearchParams);
    expect(String(fetchMock.mock.calls[0][1].body)).toContain('name=alice');
  });

  it('注册提交到 /register，并在填写邮箱时带上 email', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, JSON.stringify({
      code: 200, message: 'ok', data: { token: 'tok', userId: 8, userName: 'bob' },
    })));
    globalThis.fetch = fetchMock;

    await tryRegister('bob', 'secret123', 'bob@example.com');

    expect(fetchMock.mock.calls[0][0]).toBe('/register');
    const body = String(fetchMock.mock.calls[0][1].body);
    expect(body).toContain('name=bob');
    expect(body).toContain('email=bob%40example.com');
  });

  it('未填邮箱时不发送空 email 参数', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, JSON.stringify({
      code: 200, message: 'ok', data: { token: 'tok', userId: 9, userName: 'carol' },
    })));
    globalThis.fetch = fetchMock;

    await tryRegister('carol', 'secret123', '');

    expect(String(fetchMock.mock.calls[0][1].body)).not.toContain('email=');
  });

  it('用户名重复时抛出后端给的原因', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(409, JSON.stringify({
      code: 409, message: '该用户名已被注册，请换一个', data: null,
    })));

    await expect(tryRegister('alice', 'secret123', '')).rejects.toThrow('该用户名已被注册，请换一个');
  });

  it('响应缺少 token 时视为失败', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeResponse(200, JSON.stringify({
      code: 200, message: 'ok', data: { userId: 1 },
    })));

    await expect(tryLogin('alice', 'secret123')).rejects.toThrow('用户名或密码不正确。');
  });
});

describe('令牌过期（401）的统一处理', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  /**
   * 之前 401 只弹一句 "Invalid or expired token"，用户被晾在页面上、后续请求全部失败。
   * 现在要清掉本地凭证（并跳登录页；node 环境没有 window.location，靠内部判断跳过）。
   */
  it('401 时清掉本地登录信息', async () => {
    localStorage.setItem('token', 'expired');
    localStorage.setItem('userId', '7');
    localStorage.setItem('userName', 'alice');
    globalThis.fetch = vi.fn().mockResolvedValue(
      fakeResponse(401, JSON.stringify({ code: 401, message: 'Invalid or expired token', data: null })),
    );

    await expect(apiRequest('/practice-sessions')).rejects.toMatchObject({ status: 401 });

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('userId')).toBeNull();
    expect(localStorage.getItem('userName')).toBeNull();
  });

  it('403 不当成令牌过期处理，本地凭证保留', async () => {
    localStorage.setItem('token', 'still-good');
    globalThis.fetch = vi.fn().mockResolvedValue(
      fakeResponse(403, JSON.stringify({ code: 403, message: '用户名或密码错误', data: null })),
    );

    await expect(apiRequest('/x')).rejects.toMatchObject({ status: 403 });

    expect(localStorage.getItem('token')).toBe('still-good');
  });
});
