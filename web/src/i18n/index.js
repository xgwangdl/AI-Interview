import { createI18n } from 'vue-i18n';
import zhCN from './messages/zh-CN';
import enUS from './messages/en-US';
import jaJP from './messages/ja-JP';

export const LOCALE_STORAGE_KEY = 'light-interview-locale';

export const LOCALE_OPTIONS = [
  { value: 'zh-CN', label: '简体中文', short: '中' },
  { value: 'en-US', label: 'English', short: 'EN' },
  { value: 'ja-JP', label: '日本語', short: '日' },
];

export function normalizeLocale(value) {
  if (!value) return 'zh-CN';
  const normalized = String(value).trim();
  if (normalized.startsWith('en')) return 'en-US';
  if (normalized.startsWith('ja')) return 'ja-JP';
  return 'zh-CN';
}

export function readLocalLocale() {
  return normalizeLocale(localStorage.getItem(LOCALE_STORAGE_KEY));
}

export const i18n = createI18n({
  legacy: false,
  locale: readLocalLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
    'ja-JP': jaJP,
  },
});

export function setLocale(value, { remember = true } = {}) {
  const locale = normalizeLocale(value);
  i18n.global.locale.value = locale;
  if (remember) localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  if (typeof document !== 'undefined') document.documentElement.lang = locale;
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('light-interview-locale-updated', { detail: { locale } }));
  }
  return locale;
}

export function bootstrapLocale() {
  return setLocale(readLocalLocale(), { remember: false });
}
