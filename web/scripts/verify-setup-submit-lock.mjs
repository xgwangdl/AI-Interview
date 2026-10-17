/**
 * 回归：创建练习按钮在等待期间必须锁住，连续点击只能发起一次出题请求。
 *
 * 用 Playwright 拦截出题与建会话接口，故意延迟 1.2 秒，模拟真实模型耗时。
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL || 'http://localhost:5173';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

const browser = await chromium.launch({ executablePath: CHROME });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
let planRequests = 0;
let failures = 0;
const check = (label, condition, detail = '') => {
  console.log(`  ${condition ? 'PASS' : 'FAIL'} ${label} ${detail}`);
  if (!condition) failures += 1;
};

try {
  await page.route('**/api/v1/practice-sessions/question-plan', async (route) => {
    planRequests += 1;
    await new Promise((resolve) => setTimeout(resolve, 1200));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        provider: 'test',
        model: 'test',
        promptVersion: 'test',
        questions: Array.from({ length: 5 }, (_, index) => ({
          id: `q-${index + 1}`, text: `测试题目 ${index + 1}`, category: '测试',
        })),
      }),
    });
  });
  await page.route('**/api/v1/practice-sessions', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    const now = new Date().toISOString();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: '11111111-1111-1111-1111-111111111111',
        createdAt: now,
        retentionUntil: new Date(Date.now() + 7 * 86400000).toISOString(),
        status: 'in_progress',
        answers: [],
      }),
    });
  });

  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', 'smoke');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await page.goto(`${BASE}/practice/setup`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="例如：Java 后端工程师"]', 'Java 后端工程师');

  const button = page.locator('button:has-text("继续检测设备")');
  await button.click();
  // 第一次点击后立刻看按钮状态；同一帧的第二次点击由函数内 guard 挡住
  await page.waitForTimeout(150);
  const disabled = await button.isDisabled();
  check('等待期间按钮不可用', disabled);
  await button.click({ force: true }).catch(() => {});
  await page.waitForTimeout(1800);
  check('连续点击只发出一次出题请求', planRequests === 1, `requests=${planRequests}`);
} catch (error) {
  console.log(`\n失败：${error.message}`);
  failures += 1;
} finally {
  await browser.close();
}

console.log(`\n结果：${failures === 0 ? '通过' : failures + ' 项失败'}`);
process.exitCode = failures === 0 ? 0 : 1;
