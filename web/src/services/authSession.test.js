import { beforeEach, describe, expect, it } from 'vitest';
import { LOCALE_STORAGE_KEY } from '../i18n';
import { clearLocalSession } from './authSession';

describe('authSession', () => {
  beforeEach(() => localStorage.clear());

  it('退出或令牌失效时清掉账号标识与语言偏好', () => {
    [
      'token', 'userId', 'userName', 'username', 'light-interview-display-name', LOCALE_STORAGE_KEY,
    ].forEach((key) => localStorage.setItem(key, 'value'));
    localStorage.setItem('light-interview-theme', 'blue');

    clearLocalSession();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('light-interview-display-name')).toBeNull();
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem('light-interview-theme')).toBe('blue');
  });
});
