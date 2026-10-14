/**
 * 用真实浏览器给正在运行的前端截图，产物放进 docs/images/。
 *
 * 用法：
 *   docker compose up -d && java -jar consumer/target/*.jar &   # 后端 8088
 *   npm --prefix web run dev &                                  # 前端 5173
 *   node web/scripts/capture-screenshots.mjs
 *
 * 之所以脚本化而不是手截：界面改一次就要重截，README 里的图容易过期。
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../docs/images');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const USER = process.env.DEMO_USER || 'smoke';
const CODE = process.env.DEMO_CODE || '123456';
// 演示用会话：一笔 Python 上机题（属于 smoke），没有就跳过那两张图。
const CODING_SESSION = process.env.CODING_SESSION || 'fcfdc48d-e50a-4c3e-8726-414ae3dc187d';

mkdirSync(OUT, { recursive: true });

// 直接用系统里已有的 Chrome（Playwright 自带的 Chromium 需要额外下载，内网环境常常下不动）。
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const browser = await chromium.launch({ channel: 'chrome', executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const shots = [];

async function capture(path, file) {
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' });
  // Monaco 与报告接口都需要一点时间，固定等一会儿比等某个选择器更稳。
  await page.waitForTimeout(2500);
  await page.screenshot({ path: `${OUT}/${file}` });
  shots.push(file);
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await page.screenshot({ path: `${OUT}/01-login.png` });
shots.push('01-login.png');

await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
await page.fill('input[placeholder="输入密码"]', CODE);
await page.click('button:has-text("安全登录")');
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });

await capture('/', '02-dashboard.png');
await capture('/practice/setup', '03-practice-setup.png');
await capture('/coding', '04-coding-setup.png');

if (CODING_SESSION) {
  await capture(`/coding/${CODING_SESSION}`, '05-coding-exam.png');
  await capture(`/coding/${CODING_SESSION}/report`, '06-coding-report.png');
}

await browser.close();
console.log(`已生成 ${shots.length} 张截图：\n${shots.join('\n')}`);
