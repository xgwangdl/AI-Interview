import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../..');
const out = resolve(root, 'data/screenshots');
const chrome = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const base = process.env.BASE_URL || 'http://localhost:5173';
const api = process.env.API_URL || 'http://localhost:8088';
const loginName = 'smoke';
const loginCode = '123456';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function apiLogin() {
  const response = await fetch(`${api}/login`, {
    method: 'POST',
    body: new URLSearchParams({ name: loginName, code: loginCode }),
  });
  const body = await response.json();
  assert(response.ok && body.data?.token, '无法登录演示账号');
  return body.data.token;
}

async function loadProfile(token) {
  const response = await fetch(`${api}/api/v1/me/profile`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert(response.ok, '无法读取演示账号资料');
  return response.json();
}

async function restoreProfile(token, profile) {
  const response = await fetch(`${api}/api/v1/me/profile`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      displayName: profile.displayName,
      email: profile.email || '',
      timezone: profile.timezone,
      fontScale: profile.fontScale,
      reduceMotion: profile.reduceMotion,
      theme: profile.theme,
    }),
  });
  assert(response.ok, '恢复演示账号资料失败');
}

async function noHorizontalOverflow(page, label) {
  const result = await page.evaluate(() => {
    const viewport = document.documentElement.clientWidth;
    const overflow = document.documentElement.scrollWidth - viewport;
    const offenders = [...document.querySelectorAll('body *')]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return {
          tag: element.tagName,
          className: typeof element.className === 'string' ? element.className : '',
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
        };
      })
      .filter((item) => item.right > viewport + 1)
      .slice(0, 12);
    return { overflow, offenders, overflowX: getComputedStyle(document.body).overflowX };
  });
  assert(result.offenders.length === 0 && (result.overflow <= 1 || result.overflowX === 'hidden'),
    `${label}存在横向溢出 ${result.overflow}px：${JSON.stringify(result.offenders)}`);
}

await mkdir(out, { recursive: true });
const token = await apiLogin();
const original = await loadProfile(token);
const browser = await chromium.launch({ executablePath: chrome, args: ['--hide-scrollbars'] });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const browserErrors = [];
page.on('pageerror', (error) => browserErrors.push(error.message));

try {
  await page.goto(`${base}/login`, { waitUntil: 'networkidle' });
  await page.locator('.login-form input').nth(0).fill(loginName);
  await page.locator('.login-form input').nth(1).fill(loginCode);
  await page.getByRole('button', { name: /安全登录/ }).click();
  await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 });

  await page.locator('.profile-trigger').click();
  await page.getByRole('menuitem', { name: '个人设置' }).click();
  await page.waitForURL('**/settings/profile');
  await page.locator('.profile-settings-page').waitFor();

  const profilePanel = page.locator('.profile-panel');
  await profilePanel.locator('.el-form-item').filter({ hasText: '昵称' }).locator('input').fill('界面验证用户');
  await page.locator('.theme-card').filter({ hasText: '藏青' }).click();
  await page.locator('.font-options button').filter({ hasText: '较大' }).click();
  const motionSwitch = page.locator('.motion-row .el-switch');
  if (!(await motionSwitch.evaluate((element) => element.classList.contains('is-checked')))) {
    await motionSwitch.click();
  }
  const saveResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/me/profile') && response.request().method() === 'PUT',
  { timeout: 5000 }).catch(() => null);
  await page.getByRole('button', { name: '保存个人设置' }).click();
  const saveResponse = await saveResponsePromise;
  if (!saveResponse) {
    const messages = await page.locator('.el-message').allInnerTexts();
    const inputs = await profilePanel.locator('input').evaluateAll((items) => items.map((item) => ({
      value: item.value,
      disabled: item.disabled,
    })));
    throw new Error(`保存按钮没有发起请求，提示=${JSON.stringify(messages)}，输入=${JSON.stringify(inputs)}`);
  }
  const saveBody = await saveResponse.text();
  assert(saveResponse.ok(), `保存资料失败：HTTP ${saveResponse.status()} ${saveBody}`);
  await page.locator('.el-message').filter({ hasText: '个人资料与外观设置已保存' }).waitFor();

  const appearance = await page.evaluate(() => ({
    theme: document.documentElement.dataset.theme,
    fontScale: document.documentElement.dataset.fontScale,
    reduceMotion: document.documentElement.dataset.reduceMotion,
    scale: getComputedStyle(document.documentElement).getPropertyValue('--ui-scale').trim(),
  }));
  assert(appearance.theme === 'blue', `主题未应用：${appearance.theme}`);
  assert(appearance.fontScale === 'large', `字号未应用：${appearance.fontScale}`);
  assert(appearance.reduceMotion === 'true', '减弱动效未应用');
  assert(appearance.scale === '108%', `界面缩放不正确：${appearance.scale}`);
  assert((await page.locator('.profile-name').innerText()).includes('界面验证用户'), '侧栏昵称未同步');
  await noHorizontalOverflow(page, '桌面布局');

  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.profile-settings-page').waitFor();
  assert((await profilePanel.locator('.el-form-item').filter({ hasText: '昵称' }).locator('input').inputValue()) === '界面验证用户',
    '刷新后昵称未持久化');
  assert((await page.locator('.profile-name').innerText()).includes('界面验证用户'), '刷新后侧栏昵称不正确');
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `${out}/profile-settings-desktop.png`, fullPage: true });

  const security = page.locator('.security-panel');
  await security.locator('input[type="password"]').nth(0).fill('wrong-password');
  await security.locator('input[type="password"]').nth(1).fill('new-password');
  await security.locator('input[type="password"]').nth(2).fill('new-password');
  await page.getByRole('button', { name: '更新密码' }).click();
  await page.getByText('当前密码不正确').waitFor();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.profile-settings-page').waitFor();
  await page.screenshot({ path: `${out}/profile-settings-mobile.png`, fullPage: true });
  await noHorizontalOverflow(page, '手机布局');
  const themeCardWidths = await page.locator('.theme-card').evaluateAll((items) => items.map((item) => item.getBoundingClientRect().width));
  assert(themeCardWidths.length === 3 && Math.max(...themeCardWidths) - Math.min(...themeCardWidths) < 2,
    '手机主题选项宽度不一致');
  assert(browserErrors.length === 0, `浏览器脚本错误：${browserErrors.join('; ')}`);
  console.log('PASS personal settings menu and profile form');
  console.log('PASS theme, font scale, reduced motion and sidebar nickname');
  console.log('PASS refresh persistence and password error handling');
  console.log('PASS desktop and mobile horizontal overflow checks');
  console.log(`SCREENSHOT ${out}/profile-settings-desktop.png`);
  console.log(`SCREENSHOT ${out}/profile-settings-mobile.png`);
} finally {
  try {
    await restoreProfile(token, original);
  } finally {
    await browser.close();
  }
}
