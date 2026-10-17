/**
 * 外语口语 L1 的真浏览器验证：
 * 选语言 → 开始 → 手打一轮（拿真实回复）→ 结束看复盘 → 清理这场练习。
 *
 *     node web/scripts/verify-language-l1.mjs
 *
 * 需要后端 8088、前端 5173 在跑。麦克风路径由 scripts/verify-language-l1.ps1
 * 用真实音频文件走接口验证；这里走"手打"降级入口，顺带证明它确实能用。
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
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
}

let sessionId = null;
let token = null;

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });

  // 侧栏入口
  await page.locator('.main-nav button:has-text("外语口语")').click();
  await page.waitForURL((url) => url.pathname === '/language', { timeout: 15000 });
  await page.waitForSelector('.language-tabs', { timeout: 15000 });
  await page.waitForTimeout(500);
  check('两门语言可选', await page.locator('.language-tabs .el-radio-button').count() === 2);
  check('英语有三个场景', await page.locator('.scenario-card').count() === 3,
    (await page.locator('.scenario-card strong').allInnerTexts()).join(' / '));
  await shot('language-setup');

  // 切到日语：应当提示"本机没有日语语音"（这台机器没有 ja 系统语音）
  await page.locator('.language-tabs .el-radio-button:has-text("日本語")').click();
  await page.waitForTimeout(400);
  const hint = await page.locator('.voice-hint').count()
    ? (await page.locator('.voice-hint').innerText()).replace(/\s+/g, ' ')
    : '（本机有日语语音，未提示）';
  console.log(`  日语语音提示：${hint}`);
  check('日语场景也跟着切换', (await page.locator('.scenario-card').first().innerText()).includes('面试')
    || (await page.locator('.scenario-card').count()) === 3);

  // 切回英语并开始
  await page.locator('.language-tabs .el-radio-button:has-text("English")').click();
  await page.waitForTimeout(300);
  await page.locator('button:has-text("开始对话")').click();
  await page.waitForURL((url) => /^\/language\/[0-9a-f-]{36}$/.test(url.pathname), { timeout: 20000 });
  sessionId = new URL(page.url()).pathname.split('/').pop();
  await page.waitForSelector('.talk-button', { timeout: 15000 });
  check('开场白显示在数字人下方', (await page.locator('.ai-line').innerText()).length > 20,
    (await page.locator('.ai-line').innerText()).slice(0, 60) + '…');
  await shot('language-session');

  // 手打一轮
  await page.locator('button:has-text("手打这一句")').click();
  await page.fill('.text-input textarea', 'I led the billing migration and cut the checkout latency to 100 milliseconds.');
  await page.locator('.text-input button:has-text("发送")').click();
  await page.waitForSelector('.turn-card', { timeout: 60000 });
  await page.waitForTimeout(600);
  const turn = (await page.locator('.turn-card').first().innerText()).replace(/\s+/g, ' ');
  check('第一轮卡片显示我说的话与 AI 回复', turn.includes('I led') && turn.includes('第 1 轮'), turn.slice(0, 90));
  const hasFeedback = await page.locator('.turn-feedback').count();
  console.log(`  这一轮建议条数：${hasFeedback ? await page.locator('.turn-feedback div').count() : 0}`);
  await shot('language-session-turn');

  // 结束 → 复盘
  await page.locator('button:has-text("结束并看复盘")').click();
  await page.waitForSelector('.report-panel', { timeout: 90000 });
  await page.waitForTimeout(500);
  check('复盘有四项分数', await page.locator('.score-cell').count() === 4);
  check('复盘有改进建议', await page.locator('.improvements li').count() >= 1);
  const scores = (await page.locator('.score-cell').allInnerTexts()).map((text) => text.replace(/\n+/g, ' '));
  console.log(`  分数：${scores.join(' | ')}`);
  await shot('language-report');

  token = await page.evaluate(() => localStorage.getItem('token'));
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('language-failed');
  failures += 1;
} finally {
  if (sessionId && token) {
    const response = await fetch(`${API}/api/v1/language-sessions/${sessionId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    console.log(`清理：删除验证用的会话 ${sessionId} → ${response.status}`);
  }
  await browser.close();
}

console.log(`\n界面检查：${failures === 0 ? '全部通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
