/**
 * 验证训练档案：仪表盘的趋势/维度/短板能显示，点「针对短板再练一场」能把
 * 岗位与弱项维度带到配置页。
 *
 *     node web/scripts/verify-practice-archive.mjs
 *
 * 需要后端（8088）与前端（5173）都在跑。用系统 Chrome。
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../data/screenshots');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const USER = process.env.DEMO_USER || 'smoke';
const CODE = process.env.DEMO_CODE || '123456';

mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
page.on('requestfailed', (request) => console.log(`  [requestfailed] ${request.url()} ${request.failure()?.errorText}`));

async function shot(name) {
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
}

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
  await page.fill('input[placeholder="输入密码"]', CODE);
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await page.waitForSelector('.archive-section', { timeout: 15000 });
  await page.waitForTimeout(800);
  await shot('archive-dashboard');

  const trendLine = await page.locator('.trend-line').count();
  const dots = await page.locator('.trend-dot').count();
  const bars = await page.locator('.dimension-row').count();
  const weak = await page.locator('.weakest-list li').count();
  const scope = await page.locator('.archive-scope').innerText();

  console.log(`\n档案：趋势线 ${trendLine} 条 / ${dots} 个点，维度 ${bars} 条，短板 ${weak} 项`);
  console.log(`范围说明：${scope}`);

  // 点「针对短板再练一场」→ 配置页应当带上岗位与弱项
  await page.click('button:has-text("针对短板再练一场")');
  await page.waitForURL((url) => url.pathname.includes('/practice/setup'), { timeout: 15000 });
  await page.waitForSelector('.focus-banner', { timeout: 10000 });
  await page.waitForTimeout(600);
  await shot('archive-focus-setup');

  const role = await page.inputValue('input[placeholder="例如：Java 后端工程师"]');
  const banner = await page.locator('.focus-banner').innerText();
  console.log(`\n配置页岗位预填：${role}`);
  console.log(`重点考察提示：${banner.replace(/\n+/g, ' / ')}`);
  console.log(`URL: ${page.url()}`);
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('archive-failed');
  process.exitCode = 1;
} finally {
  await browser.close();
}
