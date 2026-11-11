/**
 * 页面主题色。
 *
 * 整个界面只有 58 处用 var(--green*)，所以换主题只需要覆盖这几个变量，
 * 不必改任何组件样式。中性灰（文字/边框）与主题无关，不需要跟着变。
 */

const STORAGE_KEY = 'light-interview-theme';

export const THEMES = {
  green: {
    label: '松绿',
    swatch: '#286956',
    vars: { '--green': '#286956', '--green-dark': '#183f34', '--green-soft': '#e6eee9' },
  },
  blue: {
    label: '藏青',
    swatch: '#476f86',
    vars: { '--green': '#476f86', '--green-dark': '#2e5266', '--green-soft': '#e6eef2' },
  },
  amber: {
    label: '赭橙',
    swatch: '#a75c2b',
    vars: { '--green': '#a75c2b', '--green-dark': '#7e431f', '--green-soft': '#f6ebe1' },
  },
};

export const DEFAULT_THEME = 'green';

export function normalizeTheme(name) {
  return Object.prototype.hasOwnProperty.call(THEMES, name) ? name : DEFAULT_THEME;
}

/** 把主题变量写到 :root 上。 */
export function applyTheme(name) {
  const theme = THEMES[normalizeTheme(name)];
  const root = document.documentElement;
  Object.entries(theme.vars).forEach(([key, value]) => root.style.setProperty(key, value));
  root.dataset.theme = normalizeTheme(name);
}

export function readLocalTheme() {
  return normalizeTheme(localStorage.getItem(STORAGE_KEY));
}

export function rememberTheme(name) {
  const theme = normalizeTheme(name);
  localStorage.setItem(STORAGE_KEY, theme);
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('light-interview-theme-updated', { detail: { theme } }));
  }
}

/** 启动时先把本地记录的主题用上，避免闪一下绿色再变成用户选的颜色。 */
export function bootstrapTheme() {
  const name = readLocalTheme();
  applyTheme(name);
  return name;
}
