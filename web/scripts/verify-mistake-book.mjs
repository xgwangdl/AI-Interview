/**
 * 错题本与额度条的真浏览器验证：
 *
 *   1. 顶栏能看到"今日 AI 额度 x/120"（额度是共享成本，得让人看得见）
 *   2. 有错题的账号：错题本列出"最差的那道题"、题面、还差多少分、重练按钮
 *   3. 点「重练这道题」→ 后端建出只有这一道题的练习 → 落到设备检测页
 *
 *     node web/scripts/verify-mistake-book.mjs
 *
 * 需要后端（8088）与前端（5173）都在跑。
 *
 * 关于登录：先用 smoke 走一遍真实登录（前端登录链路）。
 * 错题本需要"有低分记录"的账号，而它只有真实用户 guangge 有 ——
 * 这里按 scripts/smoke-test.ps1 的做法用开发密钥签一个 token 换过来，
 * 不猜测、也不修改任何人的密码。
 */
import { chromium } from 'playwright';
import { createHmac } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../data/screenshots');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const API = process.env.API_URL || 'http://localhost:8088';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const MISTAKE_USER = process.env.MISTAKE_USER || 'guangge';
const SECRET = process.env.AUTH_TOKEN_SECRET || 'change-this-development-secret';

mkdirSync(OUT, { recursive: true });

const b64 = (value) => Buffer.from(value).toString('base64url');
function tokenFor(userName) {
  const header = b64(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64(JSON.stringify({ userName, exp: Math.floor(Date.now() / 1000) + 3600 }));
  const signature = createHmac('sha256', SECRET).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${signature}`;
}

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));

async function shot(name) {
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
}

let createdSessionId = null;

try {
  // 1) 真实登录 + 额度条
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await page.waitForSelector('.quota-chip', { timeout: 15000 });
  console.log(`\n额度条：${await page.locator('.quota-chip').innerText()}`);

  // 2) 换成有错题记录的账号看错题本
  await page.evaluate(([name, token]) => {
    localStorage.setItem('userName', name);
    localStorage.setItem('token', token);
  }, [MISTAKE_USER, tokenFor(MISTAKE_USER)]);
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.mistake-section', { timeout: 20000 });
  await page.waitForTimeout(800);
  await shot('mistake-book');

  const rows = await page.locator('.mistake-row').count();
  const scope = await page.locator('.mistake-section .archive-scope').innerText();
  const first = await page.locator('.mistake-row').first().innerText();
  console.log(`\n错题本：${rows} 行`);
  console.log(`范围说明：${scope}`);
  console.log(`第一条：${first.replace(/\n+/g, ' | ')}`);

  // 3) 重练这道题 → 设备检测页
  await page.locator('.mistake-row').first().locator('button:has-text("重练这道题")').click();
  await page.waitForURL((url) => url.pathname.includes('/practice/device-check'), { timeout: 20000 });
  await page.waitForTimeout(1200);
  await shot('mistake-retry-device-check');
  createdSessionId = new URL(page.url()).searchParams.get('id');
  console.log(`\n重练落到设备检测页：${page.url()}`);
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('mistake-book-failed');
  process.exitCode = 1;
} finally {
  // 清理：这次验证建出来的练习不属于任何人，用完就删（顺带验证删除接口还能用）
  if (createdSessionId) {
    const response = await fetch(`${API}/api/v1/practice-sessions/${createdSessionId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${tokenFor(MISTAKE_USER)}` },
    });
    console.log(`清理：删除验证用的练习 ${createdSessionId} → ${response.status}`);
  }
  await browser.close();
}
