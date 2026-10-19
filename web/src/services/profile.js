import { apiRequest } from './api';
import { applyTheme, normalizeTheme, rememberTheme } from './theme';

const DISPLAY_NAME_KEY = 'light-interview-display-name';

export const TIMEZONE_OPTIONS = [
  { value: 'Asia/Shanghai', label: '中国标准时间（Asia/Shanghai）' },
  { value: 'Asia/Tokyo', label: '日本标准时间（Asia/Tokyo）' },
  { value: 'Asia/Singapore', label: '新加坡时间（Asia/Singapore）' },
  { value: 'Australia/Sydney', label: '悉尼时间（Australia/Sydney）' },
  { value: 'Europe/London', label: '伦敦时间（Europe/London）' },
  { value: 'America/New_York', label: '纽约时间（America/New_York）' },
  { value: 'America/Los_Angeles', label: '洛杉矶时间（America/Los_Angeles）' },
  { value: 'UTC', label: '协调世界时（UTC）' },
];

export const FONT_SCALE_OPTIONS = [
  { value: 'standard', label: '标准', sample: 'Aa', scale: '100%' },
  { value: 'large', label: '较大', sample: 'Aa', scale: '108%' },
  { value: 'xlarge', label: '特大', sample: 'Aa', scale: '115%' },
];

const FONT_SCALES = Object.fromEntries(FONT_SCALE_OPTIONS.map((item) => [item.value, item.scale]));

export function loadProfile() {
  return apiRequest('/me/profile');
}

export function saveProfile(payload) {
  return apiRequest('/me/profile', { method: 'PUT', body: JSON.stringify(payload) });
}

export function changePassword(payload) {
  return apiRequest('/me/password', { method: 'PUT', body: JSON.stringify(payload) });
}

export function profileDisplayName(profile, fallback = '练习者') {
  return profile?.displayName?.trim() || profile?.loginName?.trim() || fallback;
}

export function normalizeFontScale(value) {
  return Object.prototype.hasOwnProperty.call(FONT_SCALES, value) ? value : 'standard';
}

export function readCachedDisplayName() {
  return localStorage.getItem(DISPLAY_NAME_KEY) || localStorage.getItem('userName') || '练习者';
}

export function rememberDisplayName(name) {
  const cleanName = String(name || '').trim();
  if (!cleanName) return;
  localStorage.setItem(DISPLAY_NAME_KEY, cleanName);
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('light-interview-profile-updated', {
      detail: { displayName: cleanName },
    }));
  }
}

/**
 * 字号通过整体界面缩放实现。现有页面大量使用固定像素字号，
 * 直接改根字号不会影响这些组件；缩放能保持所有控件比例一致。
 */
export function applyAppearanceSettings(settings = {}) {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  const fontScale = normalizeFontScale(settings.fontScale);
  root.dataset.fontScale = fontScale;
  root.style.setProperty('--ui-scale', FONT_SCALES[fontScale]);
  root.dataset.reduceMotion = settings.reduceMotion ? 'true' : 'false';
  if (settings.theme) {
    const theme = normalizeTheme(settings.theme);
    applyTheme(theme);
    rememberTheme(theme);
  }
}

export function profileInitial(name) {
  return String(name || '练').trim().slice(0, 1) || '练';
}

export function formatJoinedAt(value, locale = 'zh-CN') {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date);
}
