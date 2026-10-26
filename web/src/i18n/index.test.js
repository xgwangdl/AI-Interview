import { beforeEach, describe, expect, it, vi } from 'vitest';
import zhCN from './messages/zh-CN';
import enUS from './messages/en-US';
import jaJP from './messages/ja-JP';
import {
  LOCALE_STORAGE_KEY, normalizeLocale, readLocalLocale, setLocale,
} from './index';

function keyPaths(value, prefix = '') {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return child && typeof child === 'object' && !Array.isArray(child)
      ? keyPaths(child, path)
      : [path];
  }).sort();
}

describe('i18n locale', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    globalThis.document = { documentElement: { lang: '' } };
  });

  it('规范化浏览器语言并安全回退到中文', () => {
    expect(normalizeLocale('en-GB')).toBe('en-US');
    expect(normalizeLocale('ja')).toBe('ja-JP');
    expect(normalizeLocale('zh-Hans')).toBe('zh-CN');
    expect(normalizeLocale('fr-FR')).toBe('zh-CN');
    expect(normalizeLocale(null)).toBe('zh-CN');
  });

  it('三种语言拥有完全一致的键集合', () => {
    const zhKeys = keyPaths(zhCN);
    expect(keyPaths(enUS)).toEqual(zhKeys);
    expect(keyPaths(jaJP)).toEqual(zhKeys);
  });

  it('切换语言会记住设置并更新 document.lang', () => {
    expect(readLocalLocale()).toBe('zh-CN');
    expect(setLocale('ja')).toBe('ja-JP');
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('ja-JP');
    expect(globalThis.document.documentElement.lang).toBe('ja-JP');
  });
});
