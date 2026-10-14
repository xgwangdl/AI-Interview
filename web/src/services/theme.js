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
    swatch: '#287b63',
    vars: { '--green': '#287b63', '--green-dark': '#1f5f4d', '--green-soft': '#e9f4ef' },
  },
  blue: {
    label: '藏青',
    swatch: '#2f6b9a',
    vars: { '--green': '#2f6b9a', '--green-dark': '#245578', '--green-soft': '#e8f0f7' },
  },
  amber: {
    label: '赭橙',
    swatch: '#b4622a',
    vars: { '--green': '#b4622a', '--green-dark': '#8f4c1f', '--green-soft': '#fbeee4' },
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
  localStorage.setItem(STORAGE_KEY, normalizeTheme(name));
}

/** 启动时先把本地记录的主题用上，避免闪一下绿色再变成用户选的颜色。 */
export function bootstrapTheme() {
  const name = readLocalTheme();
  applyTheme(name);
  return name;
}
