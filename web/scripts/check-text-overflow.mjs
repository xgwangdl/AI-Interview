/**
 * 字号调整后的回归检查：找出「文字被容器裁掉」的元素。
 *
 *     node web/scripts/check-text-overflow.mjs <sessionId> <codingSessionId>
 *
 * 判断依据：元素自身 overflow 是 hidden/clip，但 scrollHeight/scrollWidth 比 client 大 ——
 * 说明内容放不下。字号整体调大后最容易出问题的就是这类固定高度的小盒子。
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL || 'http://localhost:5173';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const USER = process.env.DEMO_USER || 'smoke';
const CODE = process.env.DEMO_CODE || '123456';
const [sessionId, codingSessionId] = process.argv.slice(2);

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

const probe = () => {
  const clipped = [];
  for (const el of document.querySelectorAll('*')) {
    const style = getComputedStyle(el);
    if (!['hidden', 'clip'].includes(style.overflow) && !['hidden', 'clip'].includes(style.overflowY)) continue;
    const text = (el.textContent || '').trim();
    if (!text) continue;
    const overY = el.scrollHeight - el.clientHeight;
    const overX = el.scrollWidth - el.clientWidth;
    // 留 2px 余量，避免把亚像素误差当成裁切
    if (overY > 2 || overX > 2) {
      clipped.push({
        tag: el.tagName.toLowerCase(),
        cls: (el.className || '').toString().slice(0, 48),
        overY, overX,
        sample: text.slice(0, 30),
      });
    }
  }
  return clipped;
};

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
  await page.fill('input[placeholder="输入密码"]', CODE);
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await page.waitForTimeout(1500);

  const routes = ['/', '/practice/setup', '/history', '/coding'];
  if (sessionId) routes.push(`/practice/${sessionId}/report`);
  if (codingSessionId) routes.push(`/coding/${codingSessionId}`, `/coding/${codingSessionId}/report`);

  let total = 0;
  for (const route of routes) {
    await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    const clipped = await page.evaluate(probe);
    console.log(`${route}: 疑似裁切 ${clipped.length} 处`);
    for (const item of clipped.slice(0, 8)) {
      console.log(`   <${item.tag} class="${item.cls}"> 溢出 x${item.overX}/y${item.overY}  「${item.sample}」`);
    }
    total += clipped.length;
  }
  console.log(`\n合计 ${total} 处需要人工确认`);
} finally {
  await browser.close();
}
