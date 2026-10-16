/**
 * 验证「从零创建」向导：能分步走完、空素材也能生成、成稿页能编辑。
 *
 *     node web/scripts/verify-resume-wizard.mjs
 *
 * 需要后端（8088）与前端（5173）都在跑，且配置了真实模型 Key。
 * 用系统 Chrome（Playwright 自带的 Chromium 在内网常下不动）。
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
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
page.on('requestfailed', (request) => console.log(`  [requestfailed] ${request.url()} ${request.failure()?.errorText}`));

const steps = [];
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

  await page.goto(`${BASE}/resumes/new`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await shot('wizard-step1');
  steps.push(await page.locator('.step-bar button').count());

  // 填一个目标岗位，再走到"工作经历"加一段，确认表单可用
  await page.fill('input[placeholder^="例如：Java 后端工程师"]', '测试开发工程师');
  await page.click('button:has-text("跳过 / 下一步")');       // 基本信息
  await page.fill('input[placeholder="例如：张三"]', '');
  await page.click('button:has-text("跳过 / 下一步")');       // 教育背景
  await page.click('button:has-text("跳过 / 下一步")');       // 工作经历
  await page.click('button:has-text("+ 再加一段工作")');
  await page.fill('input[placeholder="例如：某某科技有限公司"]', '某某科技');
  await page.fill('input[placeholder="例如：后端开发工程师"]', '测试开发工程师');
  await shot('wizard-work-entry');
  const entries = await page.locator('.entry-card').count();

  // 跳到技能，填一项技能，然后生成（其余留空 → 应当出占位符骨架）
  await page.click('button:has-text("跳过 / 下一步")');       // 项目经历
  await page.click('button:has-text("跳过 / 下一步")');       // 技能与证书
  await page.fill('textarea[placeholder^="例如：Java、Spring Boot"]', 'Python、pytest、Docker');
  await page.click('button:has-text("跳过 / 下一步")');       // 其他（最后一步）
  await page.click('button:has-text("生成简历")');
  await page.waitForSelector('.draft-preview', { timeout: 90000 });
  await page.waitForTimeout(800);
  await shot('wizard-generated');

  const preview = await page.locator('.draft-preview').innerText();
  const marks = await page.locator('.draft-preview mark').count();
  const questions = await page.locator('.question-list li').count();
  const pending = await page.locator('.pending').innerText().catch(() => '');

  console.log('\n结果：');
  console.log(`  步骤数：${steps[0]}`);
  console.log(`  工作经历卡片：${entries}`);
  console.log(`  成稿里高亮的待补充：${marks} 处（${pending}）`);
  console.log(`  追问条数：${questions}`);
  console.log(`  成稿前 8 行：\n${preview.split('\n').slice(0, 8).map((line) => `    |${line}`).join('\n')}`);
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('wizard-failed');
  process.exitCode = 1;
} finally {
  await browser.close();
}
