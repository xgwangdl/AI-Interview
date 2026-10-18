import { existsSync } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..');
const repoRoot = resolve(webRoot, '..');
const outDir = resolve(repoRoot, 'reports/evidence/auth');
const baseUrl = process.env.BASE_URL || 'http://localhost:5173';

const browserCandidates = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  chromium.executablePath(),
].filter(Boolean);
const executablePath = browserCandidates.find((candidate) => existsSync(candidate));

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function waitForFrontend() {
  for (let index = 0; index < 30; index += 1) {
    try {
      const response = await fetch(`${baseUrl}/login`);
      if (response.ok) return;
    } catch {
      // Vite 可能仍在启动。
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 500));
  }
  throw new Error(`前端不可用：${baseUrl}。请先执行 npm run dev。`);
}

await mkdir(outDir, { recursive: true });
await waitForFrontend();
assert(executablePath, '没有找到 Chrome/Edge；请设置 CHROME_PATH 或执行 npx playwright install chromium');

const browser = await chromium.launch({ executablePath, headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const errors = [];
const failedResponses = [];
page.on('pageerror', (error) => errors.push(error.message));
page.on('console', (message) => {
  if (message.type() === 'error' && !message.text().startsWith('Failed to load resource')) {
    errors.push(message.text());
  }
});
page.on('response', (response) => {
  if (response.status() >= 400 && !response.url().endsWith('/favicon.ico')) {
    failedResponses.push(`${response.status()} ${response.url()}`);
  }
});

try {
  await page.goto(`${baseUrl}/login?redirect=/`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: '登录你的练习空间' }).waitFor();

  await page.getByRole('tab', { name: '注册' }).click();
  await page.getByRole('heading', { name: '创建你的练习账号' }).waitFor();
  await page.getByLabel('用户名').first().waitFor();
  await page.getByLabel('设置密码').waitFor();
  await page.getByLabel('确认密码').waitFor();
  await page.getByLabel('邮箱（可选）').waitFor();

  await page.getByRole('tab', { name: '登录' }).click();
  await page.getByRole('button', { name: '安全登录' }).click();
  await page.getByText('请输入用户名和密码。').waitFor();

  await page.goto(`${baseUrl}/practice/setup`, { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/login\?redirect=/, { timeout: 5000 });

  assert(errors.length === 0, `页面存在脚本错误：${errors.join(' | ')}`);
  assert(failedResponses.length === 0, `页面存在失败请求：${failedResponses.join(' | ')}`);
  const screenshot = resolve(outDir, 'browser-auth-smoke.png');
  await page.screenshot({ path: screenshot, fullPage: true });
  await writeFile(resolve(outDir, 'browser-auth-smoke.txt'), [
    `baseUrl=${baseUrl}`,
    `browser=${executablePath}`,
    'login/register render=PASS',
    'empty login validation=PASS',
    'protected route redirect=PASS',
    `console/page errors=${errors.length}`,
    `failed responses=${failedResponses.length}`,
  ].join('\n'), 'utf8');

  console.log(`PASS login/register render (${executablePath})`);
  console.log('PASS empty login validation');
  console.log('PASS protected route redirect');
  console.log(`SCREENSHOT ${screenshot}`);
} catch (error) {
  const failureScreenshot = resolve(outDir, 'browser-auth-smoke-failure.png');
  await page.screenshot({ path: failureScreenshot, fullPage: true }).catch(() => {});
  await writeFile(resolve(outDir, 'browser-auth-smoke-failure.txt'), [
    `baseUrl=${baseUrl}`,
    `browser=${executablePath}`,
    `error=${error?.stack || error}`,
    `console/page errors=${errors.join(' | ')}`,
    `failed responses=${failedResponses.join(' | ')}`,
  ].join('\n'), 'utf8');
  throw error;
} finally {
  await browser.close();
}
