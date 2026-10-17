/**
 * Phase 4 真浏览器验证：本周报告 + 提醒设置保存。
 */
import { chromium } from 'playwright';
import { execSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../data/screenshots');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PG = process.env.PG_CONTAINER || 'light-interview-postgres';
mkdirSync(OUT, { recursive: true });

let failures = 0;
const check = (label, condition, detail = '') => {
  console.log(`  ${condition ? 'PASS' : 'FAIL'} ${label} ${detail}`);
  if (!condition) failures += 1;
};

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
const shot = async (name) => {
  await page.waitForTimeout(350);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
};

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });

  await page.goto(`${BASE}/progress/weekly`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.weekly-page .metric-grid', { timeout: 20000 });
  check('周报页显示四项指标', await page.locator('.metric').count() === 4);
  check('周报页有下周建议', await page.locator('.suggestion-panel li').count() >= 1);
  await shot('phase4-weekly-report');

  await page.goto(`${BASE}/settings/notifications`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.notification-card', { timeout: 20000 });
  const dailySwitch = page.locator('.setting-row').filter({ hasText: '每天提醒我练题' }).locator('.el-switch');
  const before = await dailySwitch.locator('input').isChecked();
  await dailySwitch.click();
  await page.locator('button:has-text("保存设置")').click();
  await page.waitForFunction(() => document.body.innerText.includes('提醒设置已保存'), null, { timeout: 15000 });
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForSelector('.notification-card', { timeout: 20000 });
  const after = await page.locator('.setting-row').filter({ hasText: '每天提醒我练题' })
    .locator('.el-switch input').isChecked();
  check('提醒开关保存并持久化', after === !before, `before=${before} after=${after}`);
  await shot('phase4-notification-settings');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('phase4-failed');
  failures += 1;
} finally {
  execSync(`docker exec ${PG} psql -U postgres -d interview -c "delete from notification_preferences where owner_id='smoke'; delete from weekly_reports where owner_id='smoke';"`,
    { stdio: 'ignore' });
  console.log('清理：验证数据已删除');
  await browser.close();
}

console.log(`\n结果：${failures === 0 ? '通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
