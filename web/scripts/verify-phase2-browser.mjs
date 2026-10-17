/**
 * Phase 2 真浏览器验证：
 * 1) 连续面试：设备检测 → 开始面试 → 自动录制（不显示重录）
 * 2) 转写校正：报告页出现修正入口 → 保存 → 触发单题重评分 → 显示"已人工修正"
 *
 * 需要后端 8088、前端 5173。用 Chrome 的 fake media device，不依赖真实摄像头。
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
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
page.on('console', (message) => {
  if (message.type() === 'error') console.log(`  [console] ${message.text()}`);
});
page.on('response', (response) => {
  if (response.url().includes('/transcript') || response.url().includes('/re-evaluate')) {
    console.log(`  [api] ${response.request().method()} ${response.url()} → ${response.status()}`);
  }
});
const shot = async (name) => {
  await page.waitForTimeout(350);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
};

let token = null;
let continuousId = null;
let reportId = null;

async function createSession(questions, mode) {
  return page.evaluate(async ({ token, questions, mode }) => {
    const response = await fetch('/api/v1/practice-sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        role: 'Java 后端工程师', company: 'Light', interviewType: 'technical',
        difficulty: 'standard', interviewerStyle: 'balanced',
        questionCount: questions.length, durationMinutes: mode === 'continuous' ? 12 : 4,
        followUpEnabled: false, mode, questions,
      }),
    });
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  }, { token, questions, mode });
}

async function deleteSession(id) {
  if (!id || !token) return;
  await fetch(`${API}/api/v1/practice-sessions/${id}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
  });
}

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  token = await page.evaluate(() => localStorage.getItem('token'));

  // 1. 连续面试
  const continuous = await createSession([
    { id: 'p2-c1', text: '请讲一个你主导的项目。', category: '项目' },
    { id: 'p2-c2', text: '接口变慢时你怎么排查？', category: '排查' },
  ], 'continuous');
  continuousId = continuous.id;
  // 用 API 造的场次不在 Pinia 本地列表里：回首页触发一次服务端刷新，再进设备检测
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.goto(`${BASE}/practice/device-check?id=${continuousId}`, { waitUntil: 'networkidle' });
  await page.locator('button:has-text("开启设备检测")').click();
  await page.waitForFunction(() => {
    const button = [...document.querySelectorAll('button')].find((item) => item.textContent.includes('进入面试'));
    return button && !button.disabled;
  }, null, { timeout: 20000 });
  await page.locator('button:has-text("进入面试")').click();
  await page.waitForSelector('.continuous-intro', { timeout: 15000 });
  check('连续面试先显示开始说明', await page.locator('.continuous-intro').isVisible());
  await shot('phase2-continuous-intro');
  await page.locator('.continuous-intro button:has-text("开始面试")').click();
  await page.waitForFunction(() => document.body.innerText.includes('正在自动录制'), null, { timeout: 30000 });
  check('开始后自动录制，不需要手动点"开始回答"', true);
  check('连续模式没有"重新录制"按钮', (await page.locator('button:has-text("重新录制")').count()) === 0);
  await shot('phase2-continuous-recording');

  // 2. 转写校正
  const reportSession = await createSession([
    { id: 'p2-r1', text: '讲一个你优化过的接口。', category: '项目' },
  ], 'per_question');
  reportId = reportSession.id;
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  const answer = await page.evaluate(async ({ token, sessionId }) => {
    const response = await fetch(`/api/v1/practice-sessions/${sessionId}/answers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ questionIndex: 0, questionId: 'p2-r1', durationSeconds: 60, startedAtOffsetSeconds: 0 }),
    });
    return response.json();
  }, { token, sessionId: reportId });
  execSync(`docker exec ${PG} psql -U postgres -d interview -c "update practice_answers set transcript_text='I lead a billing migration last year.', transcript_status='completed', media_key='phase2-browser-dummy', media_content_type='video/webm', evaluation_status='completed' where id='${answer.id}';"`,
    { stdio: 'ignore' });
  await page.goto(`${BASE}/practice/${reportId}/report`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.transcript-edit-bar', { timeout: 20000 });
  check('报告页出现转写校正入口', await page.locator('.transcript-edit-bar').isVisible());
  await page.locator('.transcript-edit-bar button:has-text("修正转写")').click();
  await page.locator('.transcript-edit-bar textarea').fill('I led a billing migration last year.');
  await page.locator('.transcript-edit-bar button:has-text("保存并重新评分")').click();
  await page.waitForFunction(() => document.body.innerText.includes('已人工修正'), null, { timeout: 20000 });
  check('修正后显示"已人工修正"', true);
  await shot('phase2-transcript-corrected');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('phase2-failed');
  failures += 1;
} finally {
  await deleteSession(continuousId);
  await deleteSession(reportId);
  console.log('清理：验证用的练习已删除');
  await browser.close();
}

console.log(`\n结果：${failures === 0 ? '通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
