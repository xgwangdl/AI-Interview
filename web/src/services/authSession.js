import { LOCALE_STORAGE_KEY } from '../i18n';

const AUTH_STORAGE_KEYS = [
  'token',
  'userId',
  'userName',
  'username',
  'light-interview-display-name',
];

/**
 * 清除与某个账号绑定的本地状态。主题是设备偏好，可以保留；
 * 语言跟随账号，退出或令牌失效时必须移除，避免下一位用户继承。
 */
export function clearLocalSession() {
  [...AUTH_STORAGE_KEYS, LOCALE_STORAGE_KEY].forEach((key) => localStorage.removeItem(key));
}
