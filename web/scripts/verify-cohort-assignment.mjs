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
const coachName = process.env.COHORT_COACH || 'guangge';
const coachCode = process.env.COHORT_CODE || '123456';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function apiLogin() {
  const response = await fetch(`${api}/login`, {
    method: 'POST',
    body: new URLSearchParams({ name: coachName, code: coachCode }),
  });
  const body = await response.json();
  assert(response.ok && body.data?.token, '无法登录小组助教账号');
  return body.data.token;
}

async function apiRequest(token, path, options = {}) {
  const response = await fetch(`${api}/api/v1${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });
  const text = await response.text();
  assert(response.ok, `${options.method || 'GET'} ${path} 失败：HTTP ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

function assignmentPayload(assignment) {
  return {
    title: assignment.title,
    description: assignment.description,
    days: assignment.days,
    itemsPerDay: assignment.itemsPerDay,
    dueAt: assignment.dueAt,
  };
}

await mkdir(out, { recursive: true });
const token = await apiLogin();
const cohorts = await apiRequest(token, '/cohorts');
const cohort = cohorts.find((item) => item.role === 'coach');
assert(cohort, '当前助教账号没有可用于验证的小组');
const assignments = await apiRequest(token, `/cohorts/${cohort.id}/assignments`);
const original = assignments[0];
assert(original, '小组中没有可用于验证的作业');

const browser = await chromium.launch({ executablePath: chrome, args: ['--hide-scrollbars'] });
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));

try {
  await page.goto(`${base}/login`, { waitUntil: 'networkidle' });
  await page.locator('.login-form input').nth(0).fill(coachName);
  await page.locator('.login-form input').nth(1).fill(coachCode);
  await page.locator('.login-submit').click();
  await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 });
  await page.goto(`${base}/cohorts/${cohort.id}`, { waitUntil: 'networkidle' });

  await page.locator('.heading-actions button').first().click();
  await page.locator('.assignment-form').waitFor();
  const fields = page.locator('.form-grid .el-form-item');
  assert(await fields.count() === 4, '作业表单应有标题、截止时间、天数、每天题目数四项');

  const layout = await fields.evaluateAll((items) => items.map((item) => {
    const label = item.querySelector('.el-form-item__label')?.getBoundingClientRect();
    const control = item.querySelector('.el-input, .el-input-number, .el-date-editor')?.getBoundingClientRect();
    const box = item.getBoundingClientRect();
    return {
      left: box.left,
      right: box.right,
      labelBottom: label?.bottom || 0,
      controlLeft: control?.left || 0,
      controlRight: control?.right || 0,
      controlTop: control?.top || 0,
    };
  }));
  layout.forEach((item) => {
    assert(item.controlLeft >= item.left - 1 && item.controlRight <= item.right + 1, '数字或时间控件超出表单列');
    assert(item.labelBottom <= item.controlTop + 1, '表单标签与输入控件发生重叠');
  });

  const dayInput = page.locator('.form-grid .el-input-number').nth(0);
  const initialDays = Number(await dayInput.locator('input').inputValue());
  await dayInput.locator('.el-input-number__increase').click();
  assert(Number(await dayInput.locator('input').inputValue()) === initialDays + 1,
    '点击加号后作业天数没有变化');
  await page.locator('.assignment-fields .soft-button').first().click();

  await page.locator('.edit-assignment-button').first().click();
  await page.locator('.assignment-form').waitFor();
  assert(Number(await page.locator('.form-grid .el-input-number').nth(0).locator('input').inputValue()) === original.days,
    '编辑表单没有回填原作业天数');
  assert(Number(await page.locator('.form-grid .el-input-number').nth(1).locator('input').inputValue()) === original.itemsPerDay,
    '编辑表单没有回填每天题目数');

  await page.locator('.form-grid .el-input-number').nth(0).locator('.el-input-number__increase').click();
  await page.locator('.form-grid .el-input-number').nth(1).locator('.el-input-number__increase').click();
  const updateResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/cohorts/${cohort.id}/assignments/${original.id}`)
      && response.request().method() === 'PUT',
  { timeout: 10000 });
  await page.locator('.assignment-fields .primary-button').click();
  assert((await updateResponse).ok(), '保存作业修改失败');
  await page.locator('.el-message').first().waitFor();
  await page.waitForFunction(({ days, items }) => {
    const rows = [...document.querySelectorAll('.member-row strong')];
    return rows.some((row) => row.textContent?.trim() === `0/${days * items}`);
  }, { days: original.days + 1, items: original.itemsPerDay + 1 }, { timeout: 10000 });

  await page.screenshot({ path: resolve(out, 'cohort-assignment-edit.png'), fullPage: true });
  assert(errors.length === 0, `页面脚本错误：${errors.join(' | ')}`);
  console.log('PASS assignment form labels and number controls do not overlap');
  console.log('PASS number controls can change days and questions per day');
  console.log('PASS existing assignment opens with original values');
  console.log('PASS assignment update persists and refreshes member totals');
  console.log(`SCREENSHOT ${resolve(out, 'cohort-assignment-edit.png')}`);
} finally {
  await apiRequest(token, `/cohorts/${cohort.id}/assignments/${original.id}`, {
    method: 'PUT',
    body: JSON.stringify(assignmentPayload(original)),
  }).catch(() => {});
  await browser.close();
}
