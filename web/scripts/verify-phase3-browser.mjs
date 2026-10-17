/**
 * Phase 3 真浏览器验证：报告页改写 → 编辑保存 → 照着改写重录 → before/after 两个版本。
 *
 * 需要后端 8088、前端 5173；Chrome 用 fake media device。
 */
import { chromium } from 'playwright';
import { execSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../data/screenshots');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const API = process.env.API_URL || 'http://localhost:8088';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PG = process.env.PG_CONTAINER || 'light-interview-postgres';
mkdirSync(OUT, { recursive: true });

let failures = 0;
const check = (label, condition, detail = '') => {
  console.log(`  ${condition ? 'PASS' : 'FAIL'} ${label} ${detail}`);
  if (!condition) failures += 1;
};

const browser = await chromium.launch({
  executablePath: CHROME,
  args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--hide-scrollbars'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
const shot = async (name) => {
  await page.waitForTimeout(350);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
};

let token = null;
let sessionId = null;

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  token = await page.evaluate(() => localStorage.getItem('token'));

  const session = await page.evaluate(async (token) => {
    const response = await fetch('/api/v1/practice-sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        role: 'Java 后端工程师', company: 'Light', interviewType: 'technical',
        difficulty: 'standard', interviewerStyle: 'balanced', questionCount: 1,
        durationMinutes: 4, followUpEnabled: false, mode: 'per_question',
        questions: [{ id: 'p3-b1', text: '讲一个你优化过的接口。', category: '项目' }],
      }),
    });
    return response.json();
  }, token);
  sessionId = session.id;
  const answer = await page.evaluate(async ({ token, sessionId }) => {
    const response = await fetch(`/api/v1/practice-sessions/${sessionId}/answers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ questionIndex: 0, questionId: 'p3-b1', durationSeconds: 60, startedAtOffsetSeconds: 0 }),
    });
    return response.json();
  }, { token, sessionId });
  execSync(`docker exec ${PG} psql -U postgres -d interview -c "update practice_answers set transcript_text='I lead a billing migration. I cut the checkout latency from 900ms to 100ms.', transcript_status='completed' where id='${answer.id}';"`,
    { stdio: 'ignore' });

  // 回首页刷新 store，再进报告
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.goto(`${BASE}/practice/${sessionId}/report`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.rewrite-panel', { timeout: 20000 });
  check('报告页出现回答改写面板', await page.locator('.rewrite-panel').isVisible());

  // 真实模型改写
  await page.locator('.rewrite-panel button:has-text("让 AI 改写")').click();
  await page.waitForSelector('.rewrite-text', { timeout: 60000 });
  const generated = (await page.locator('.rewrite-text').innerText()).trim();
  check('AI 改写生成成功', generated.length > 10, generated.slice(0, 80));
  await shot('phase3-rewrite-generated');

  // 用户编辑并保存
  await page.locator('.rewrite-panel button:has-text("编辑")').click();
  await page.locator('.rewrite-panel textarea').fill(`${generated} In 2024, this reduced checkout latency to 100ms.`);
  await page.locator('.rewrite-panel button:has-text("保存改写")').click();
  await page.waitForFunction(() => document.body.innerText.includes('已编辑'), null, { timeout: 20000 });
  check('用户编辑保存成功', true);

  // 照着改写重录
  await page.locator('.rewrite-panel button:has-text("照着重录")').click();
  await page.waitForURL((url) => url.pathname.includes('/retake/'), { timeout: 15000 });
  await page.waitForSelector('.retake-record video', { timeout: 15000 });
  await page.waitForFunction(() => {
    const button = document.querySelector('.retake-actions button');
    return button && !button.disabled;
  }, null, { timeout: 15000 });
  await page.locator('.retake-actions button:has-text("开始重录")').click();
  await page.waitForTimeout(2200);
  await page.locator('.retake-actions button:has-text("结束并保存")').click();
  await page.waitForURL((url) => url.pathname.endsWith('/report'), { timeout: 60000 });
  await page.waitForFunction(() => document.querySelectorAll('.attempt-card').length === 2,
    null, { timeout: 60000 });
  check('重录后出现 before/after 两个版本', true);
  await shot('phase3-before-after');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('phase3-failed');
  failures += 1;
} finally {
  if (sessionId && token) {
    const response = await fetch(`${API}/api/v1/practice-sessions/${sessionId}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
    });
    console.log(`清理：练习 ${sessionId} → ${response.status}`);
  }
  await browser.close();
}

console.log(`\n结果：${failures === 0 ? '通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
