import { beforeEach, describe, expect, it, vi } from 'vitest';
import { installAuthGuard } from './authGuard';

function routerWithGuard() {
  const holder = { guard: null };
  return {
    holder,
    beforeEach(fn) {
      holder.guard = fn;
    },
  };
}

describe('authGuard', () => {
  let guard;

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    const router = routerWithGuard();
    installAuthGuard(router);
    guard = router.holder.guard;
  });

  it('没有 token 时跳登录页，并记住原本要去的地址', () => {
    const result = guard({ name: 'setup', fullPath: '/practice/setup' });

    expect(result).toEqual({ name: 'login', query: { redirect: '/practice/setup' } });
  });

  it('已登录时访问登录页直接送去总览', () => {
    localStorage.setItem('token', 'tok-1');

    expect(guard({ name: 'login', fullPath: '/login' })).toEqual({ name: 'dashboard' });
  });

  it('token 校验通过后放行，并且同一个 token 只校验一次', async () => {
    localStorage.setItem('token', 'tok-1');
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ code: 200, data: 'alice' }),
    });
    globalThis.fetch = fetchMock;

    await expect(guard({ name: 'dashboard', fullPath: '/' })).resolves.toBe(true);
    expect(localStorage.getItem('userName')).toBe('alice');

    // 第二次导航用的是缓存过的校验结果，不再打接口
    expect(guard({ name: 'history', fullPath: '/history' })).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('token 失效时清空本地凭证并跳回登录页', async () => {
    localStorage.setItem('token', 'tok-1');
    localStorage.setItem('userId', 'u1');
    localStorage.setItem('userName', 'alice');
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: false, json: async () => ({ code: 401 }) });

    const result = await guard({ name: 'dashboard', fullPath: '/' });

    expect(result).toEqual({ name: 'login', query: { redirect: '/' } });
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('userId')).toBeNull();
    expect(localStorage.getItem('userName')).toBeNull();
  });
});
