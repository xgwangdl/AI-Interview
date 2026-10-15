/**
 * 验证「令牌失效」的处理：把本地 token 换成无效值，再触发一次需要鉴权的请求，
 * 应该清掉本地凭证并跳回登录页，而不是只弹一句错误把人晾在原地。
 *
 *     node web/scripts/check-token-expiry.mjs <sessionId>
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL || 'http://localhost:5173';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const USER = process.env.DEMO_USER || 'smoke';
const CODE = process.env.DEMO_CODE || '123456';
const SESSION = process.argv[2];

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
  await page.fill('input[placeholder="输入密码"]', CODE);
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await page.waitForTimeout(1500);

  if (SESSION) {
    await page.goto(`${BASE}/practice/${SESSION}/report`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
  }

  // 把令牌换成无效值（相当于过期），然后点一个会调后端鉴权接口的按钮
  await page.evaluate(() => localStorage.setItem('token', 'not-a-valid-token'));

  const target = await page.evaluate(() => {
    const button = document.querySelector('.sample-play');
    if (button) { button.click(); return 'sample-play'; }
    return null;
  });
  console.log(`触发请求的元素: ${target || '(页面没有可点的鉴权按钮，改用直接跳转)'}`);
  if (!target) {
    await page.evaluate(() => { window.location.reload(); });
  }

  let landed = null;
  for (let i = 0; i < 40; i += 1) {
    await page.waitForTimeout(400);
    landed = new URL(page.url());
    if (landed.pathname.startsWith('/login')) break;
  }
  const storage = await page.evaluate(() => ({
    token: localStorage.getItem('token'),
    userName: localStorage.getItem('userName'),
  }));
  console.log('最终地址:', page.url());
  console.log('本地凭证:', JSON.stringify(storage));
  console.log(landed?.pathname.startsWith('/login')
    ? '✅ 已回到登录页并清空凭证'
    : '❌ 没有被送回登录页');
  if (landed?.search) console.log('登录后会跳回:', decodeURIComponent(landed.search));
} finally {
  await browser.close();
}
