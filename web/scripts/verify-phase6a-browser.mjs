/**
 * Phase 6A 真浏览器验证：助教建组/布置作业，学员加入后看到作业与分数共享开关。
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

const learner = `p6b${Date.now()}`;
const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const studentPage = await browser.newPage({ viewport: { width: 1280, height: 900 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
const shot = async (p, name) => {
  await p.waitForTimeout(350);
  await p.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
};

let cohortId = null;
let assignmentId = null;

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });

  await page.locator('.main-nav button:has-text("学习小组")').click();
  await page.waitForURL((url) => url.pathname === '/cohorts', { timeout: 15000 });
  await page.fill('input[placeholder="例如：2026 秋招 Java 训练营"]', 'Phase 6A 浏览器小组');
  await page.locator('.entry-card button:has-text("创建")').click();
  await page.waitForURL((url) => /^\/cohorts\/[0-9a-f-]{36}$/.test(url.pathname), { timeout: 20000 });
  cohortId = new URL(page.url()).pathname.split('/').pop();
  await page.waitForSelector('.heading-actions', { timeout: 20000 });
  check('助教创建小组后进入详情', await page.locator('.heading-actions').isVisible());

  const inviteCode = (await page.locator('.heading-actions .invite-button').innerText()).replace(/\s+/g, ' ').trim();
  check('详情页显示邀请码', inviteCode.length >= 8, inviteCode);
  await page.locator('button:has-text("布置作业")').first().click();
  await page.fill('.assignment-form input[placeholder="例如：本周项目追问 5 天训练"]', '5 天项目追问训练');
  await page.locator('.assignment-form button:has-text("布置作业")').click();
  await page.waitForSelector('.assignment-card', { timeout: 20000 });
  assignmentId = await page.evaluate(async ({ token, cohortId }) => {
    const response = await fetch(`/api/v1/cohorts/${cohortId}/assignments`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const list = await response.json();
    return list[0]?.id || null;
  }, { token: await page.evaluate(() => localStorage.getItem('token')), cohortId });
  check('作业出现在小组详情', await page.locator('.assignment-card').count() === 1);
  await shot(page, 'phase6-coach-cohort');

  // 注册一个测试学员并加入小组
  const register = await fetch(`${API}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ name: learner, code: '123456' }),
  }).then((response) => response.json());
  const learnerToken = register.data.token;
  await fetch(`${API}/api/v1/cohorts/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${learnerToken}` },
    body: JSON.stringify({ inviteCode: inviteCode.split(' ').pop() }),
  });

  await studentPage.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await studentPage.evaluate(({ token, name }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('userName', name);
  }, { token: learnerToken, name: learner });
  await studentPage.goto(`${BASE}/cohorts/${cohortId}`, { waitUntil: 'networkidle' });
  await studentPage.waitForSelector('.sharing-card', { timeout: 20000 });
  check('学员看到分数共享开关', await studentPage.locator('.sharing-card').isVisible());
  check('学员看到作业', await studentPage.locator('.assignment-card').count() >= 1);
  check('学员有去今日训练入口', await studentPage.locator('button:has-text("去今日训练")').count() >= 1);
  await shot(studentPage, 'phase6-learner-cohort');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot(page, 'phase6a-failed');
  failures += 1;
} finally {
  if (assignmentId) {
    execSync(`docker exec ${PG} psql -U postgres -d interview -c "delete from study_plan_items where assignment_id='${assignmentId}';"`,
      { stdio: 'ignore' });
  }
  if (cohortId) {
    execSync(`docker exec ${PG} psql -U postgres -d interview -c "delete from cohorts where id='${cohortId}';"`,
      { stdio: 'ignore' });
  }
  execSync(`docker exec ${PG} psql -U postgres -d interview -c "delete from study_plans where owner_id='${learner}'; delete from candidates where candidate_name='${learner}'; delete from notification_preferences where owner_id='${learner}';"`,
    { stdio: 'ignore' });
  console.log('清理：验证数据已删除');
  await browser.close();
}

console.log(`\n结果：${failures === 0 ? '通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
