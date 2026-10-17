/**
 * Phase 1 真浏览器验证：
 * 添加目标 → 目标详情出现今日 3 题 → 首页出现目标卡与今日计划 → 开始一题落到设备检测。
 *
 *     node web/scripts/verify-goals-phase1.mjs
 *
 * 需要后端 8088 与前端 5173。验证结束删除目标与创建的练习。
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../data/screenshots');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const API = process.env.API_URL || 'http://localhost:8088';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

mkdirSync(OUT, { recursive: true });
let failures = 0;
function check(label, condition, detail = '') {
  console.log(`  ${condition ? 'PASS' : 'FAIL'} ${label} ${detail}`);
  if (!condition) failures += 1;
}

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));

async function shot(name) {
  await page.waitForTimeout(350);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
}

let token = null;
let goalId = null;
let sessionId = null;

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  token = await page.evaluate(() => localStorage.getItem('token'));

  // 1. 目标列表 → 新建
  await page.locator('.main-nav button:has-text("求职目标")').click();
  await page.waitForURL((url) => url.pathname === '/goals', { timeout: 15000 });
  await page.locator('button:has-text("添加目标")').first().click();
  await page.waitForURL((url) => url.pathname === '/goals/new', { timeout: 15000 });
  await page.fill('input[placeholder="例如：字节跳动"]', 'Light');
  await page.fill('input[placeholder="例如：Java 后端工程师"]', 'Java 后端工程师');
  await page.locator('button:has-text("创建目标")').click();
  await page.waitForURL((url) => /^\/goals\/[0-9a-f-]{36}$/.test(url.pathname), { timeout: 20000 });
  goalId = new URL(page.url()).pathname.split('/').pop();
  await page.waitForSelector('.today-panel', { timeout: 15000 });
  await page.waitForFunction(() => document.querySelectorAll('.plan-row').length === 3,
    null, { timeout: 20000 });
  check('创建目标后进入详情', (await page.locator('.title-row h1').innerText()).includes('Java 后端工程师'));
  check('详情显示今日 3 题', (await page.locator('.plan-row').count()) === 3,
    (await page.locator('.plan-row .plan-main strong').allInnerTexts()).join(' / '));
  await shot('goal-detail');

  // 2. 首页出现目标卡与今日计划
  await page.locator('.brand').click();
  await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 });
  await page.waitForFunction(() =>
    document.querySelector('.goal-focus-title')?.textContent?.includes('Java 后端工程师'), null, { timeout: 20000 });
  await page.waitForFunction(() => document.querySelectorAll('.today-focus-row').length === 3,
    null, { timeout: 20000 });
  check('首页目标卡显示新目标', (await page.locator('.goal-focus-title').innerText()).includes('Java 后端工程师'));
  check('首页今日计划 3 条', (await page.locator('.today-focus-row').count()) === 3);
  await shot('dashboard-goal-today');

  // 3. 从首页开始第一题
  await page.locator('.today-focus-row').first().locator('button:has-text("开始")').click();
  await page.waitForURL((url) => url.pathname.includes('/practice/device-check'), { timeout: 20000 });
  sessionId = new URL(page.url()).searchParams.get('id');
  check('开始一题落到设备检测页', Boolean(sessionId), page.url());
  await shot('goal-plan-start-device-check');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('goals-phase1-failed');
  failures += 1;
} finally {
  if (token) {
    if (sessionId) {
      const response = await fetch(`${API}/api/v1/practice-sessions/${sessionId}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
      });
      console.log(`清理：练习 ${sessionId} → ${response.status}`);
    }
    if (goalId) {
      const response = await fetch(`${API}/api/v1/goals/${goalId}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
      });
      console.log(`清理：目标 ${goalId} → ${response.status}`);
    }
  }
  await browser.close();
}

console.log(`\n界面检查：${failures === 0 ? '全部通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
