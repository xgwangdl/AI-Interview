/**
 * Phase 6B/6C 真浏览器验证：助教看求助并回复；学员看反馈、提交匿名互评；对方收到匿名评价。
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
const suffix = Date.now();
const alice = `p6ba${suffix}`;
const bob = `p6bb${suffix}`;
let cohortId = null;
let aliceSession = null;
let bobSession = null;
let coachToken = null;

async function jsonFetch(path, options = {}) {
  const response = await fetch(`${API}${path}`, options);
  if (!response.ok) throw new Error(`${path} → ${response.status} ${await response.text()}`);
  return response.status === 204 ? null : response.json();
}

async function createCompletedSession(token, qid, transcript) {
  const session = await jsonFetch('/api/v1/practice-sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      role: 'Java 后端', interviewType: 'technical', difficulty: 'standard', interviewerStyle: 'balanced',
      questionCount: 1, durationMinutes: 4, followUpEnabled: false, mode: 'per_question',
      questions: [{ id: qid, text: '请讲一个你优化过的接口。', category: '项目' }],
    }),
  });
  const answer = await jsonFetch(`/api/v1/practice-sessions/${session.id}/answers`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ questionIndex: 0, questionId: qid, durationSeconds: 60, startedAtOffsetSeconds: 0 }),
  });
  execSync(`docker exec ${PG} psql -U postgres -d interview -c "update practice_sessions set status='completed' where id='${session.id}'; update practice_answers set transcript_text='${transcript}', transcript_status='completed' where id='${answer.id}';"`,
    { stdio: 'ignore' });
  return { session, answer };
}

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const coachPage = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const alicePage = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const bobPage = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const shot = async (p, name) => {
  await p.waitForTimeout(350);
  await p.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
};
alicePage.on('response', (response) => {
  if (response.url().includes('/peer-reviews')) {
    console.log(`  [api] ${response.request().method()} ${response.url()} → ${response.status()}`);
  }
});

try {
  const coachLogin = await fetch(`${API}/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ name: 'smoke', code: '123456' }),
  }).then((response) => response.json());
  coachToken = coachLogin.data.token;
  const aliceReg = await fetch(`${API}/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ name: alice, code: '123456' }),
  }).then((response) => response.json());
  const bobReg = await fetch(`${API}/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ name: bob, code: '123456' }),
  }).then((response) => response.json());
  const aliceToken = aliceReg.data.token;
  const bobToken = bobReg.data.token;

  const cohort = await jsonFetch('/api/v1/cohorts', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${coachToken}` },
    body: JSON.stringify({ name: 'P6BC 浏览器小组' }),
  });
  cohortId = cohort.id;
  for (const token of [aliceToken, bobToken]) {
    await jsonFetch('/api/v1/cohorts/join', {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ inviteCode: cohort.inviteCode }),
    });
  }
  aliceSession = await createCompletedSession(aliceToken, 'p6bc-ba', '我优化了订单接口，但说不清具体怎么做的。');
  bobSession = await createCompletedSession(bobToken, 'p6bc-bb', '我把三次查询合并成一次，接口从 900ms 降到 100ms。');

  await jsonFetch(`/api/v1/cohorts/${cohortId}/help`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${aliceToken}` },
    body: JSON.stringify({ sessionId: aliceSession.session.id, questionIndex: 0, answerId: aliceSession.answer.id,
      note: '我不知道怎么把优化过程说清楚' }),
  });
  for (const token of [aliceToken, bobToken]) {
    await jsonFetch(`/api/v1/cohorts/${cohortId}/members/me/peer-review`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ enabled: true }),
    });
  }

  // 助教处理求助
  await coachPage.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await coachPage.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await coachPage.fill('input[placeholder="输入密码"]', '123456');
  await coachPage.click('button:has-text("安全登录")');
  await coachPage.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await coachPage.goto(`${BASE}/cohorts/${cohortId}`, { waitUntil: 'networkidle' });
  await coachPage.waitForSelector('.help-queue .help-row', { timeout: 20000 });
  check('助教看到求助队列', await coachPage.locator('.help-queue .help-row').count() === 1);
  await coachPage.locator('.help-feedback textarea').fill('先讲瓶颈，再讲你做了什么改动，最后给数字结果。');
  await coachPage.locator('.help-feedback button:has-text("发送反馈")').click();
  await coachPage.waitForFunction(() => document.body.innerText.includes('目前没有学员求助'), null, { timeout: 15000 });
  check('助教反馈后队列清空', true);
  await shot(coachPage, 'phase6b-coach-feedback');

  // Alice 看反馈并提交互评
  await alicePage.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await alicePage.evaluate(({ token, name }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('userName', name);
  }, { token: aliceToken, name: alice });
  await alicePage.goto(`${BASE}/cohorts/${cohortId}`, { waitUntil: 'networkidle' });
  await alicePage.waitForSelector('.help-queue', { timeout: 20000 });
  check('学员看到助教反馈', (await alicePage.locator('.help-queue').innerText()).includes('先讲瓶颈'));
  await alicePage.waitForSelector('.peer-panel .peer-task', { timeout: 20000 });
  check('学员看到同伴匿名转写', (await alicePage.locator('.peer-task').first().innerText()).includes('同伴'));
  await alicePage.locator('.peer-task button:has-text("评价这条")').first().click();
  await alicePage.locator('.review-form textarea').fill('结果很清楚，可以再补一句对线上稳定性的影响。');
  await alicePage.locator('.review-form button:has-text("提交互评")').click();
  await alicePage.waitForTimeout(800);
  console.log(`  互评提示：${(await alicePage.locator('.el-message').allInnerTexts()).join(' / ')}`);
  await alicePage.waitForFunction(() => document.body.innerText.includes('互评已提交'), null, { timeout: 15000 });
  await shot(alicePage, 'phase6c-peer-review-form');

  // Bob 收到匿名评价
  await bobPage.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await bobPage.evaluate(({ token, name }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('userName', name);
  }, { token: bobToken, name: bob });
  await bobPage.goto(`${BASE}/cohorts/${cohortId}`, { waitUntil: 'networkidle' });
  await bobPage.waitForSelector('.received-review', { timeout: 20000 });
  const received = await bobPage.locator('.received-review').first().innerText();
  check('对方收到匿名互评', received.includes('同伴') && received.includes('稳定性'), received.replace(/\n+/g, ' '));
  await shot(bobPage, 'phase6c-peer-review-received');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot(coachPage, 'phase6bc-failed');
  failures += 1;
} finally {
  for (const session of [aliceSession?.session, bobSession?.session]) {
    if (session && coachToken) {
      await fetch(`${API}/api/v1/practice-sessions/${session.id}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${coachToken}` },
      }).catch(() => {});
    }
  }
  if (cohortId) {
    execSync(`docker exec ${PG} psql -U postgres -d interview -c "delete from cohorts where id='${cohortId}';"`, { stdio: 'ignore' });
  }
  execSync(`docker exec ${PG} psql -U postgres -d interview -c "delete from practice_sessions where owner_id in ('${alice}','${bob}'); delete from study_plans where owner_id in ('${alice}','${bob}'); delete from candidates where candidate_name in ('${alice}','${bob}');"`,
    { stdio: 'ignore' });
  console.log('清理：验证数据已删除');
  await browser.close();
}

console.log(`\n结果：${failures === 0 ? '通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
