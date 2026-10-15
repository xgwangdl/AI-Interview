/**
 * 验证报告页「让面试官念一遍示范回答」：点按钮后音频播放、旁边的小数字人嘴型跟着动。
 *
 *     node web/scripts/capture-report-sample-answer.mjs <sessionId>
 *
 * 需要后端与前端都在跑。用系统 Chrome（Playwright 自带的 Chromium 在内网常下不动）。
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../docs/images');
const BASE = process.env.BASE_URL || 'http://localhost:5173';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const USER = process.env.DEMO_USER || 'smoke';
const CODE = process.env.DEMO_CODE || '123456';
const SESSION = process.argv[2] || process.env.SESSION_ID;

if (!SESSION) {
  console.error('用法：node web/scripts/capture-report-sample-answer.mjs <sessionId>');
  process.exit(1);
}
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  executablePath: CHROME,
  args: ['--hide-scrollbars', '--autoplay-policy=no-user-gesture-required'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('console', (message) => console.log(`  [console.${message.type()}] ${message.text()}`));
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));
page.on('requestfailed', (request) => console.log(`  [requestfailed] ${request.url()} ${request.failure()?.errorText}`));

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
  await page.fill('input[placeholder="输入密码"]', CODE);
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  await page.waitForTimeout(1500);      // 等练习列表加载完（报告页依赖它）

  await page.goto(`${BASE}/practice/${SESSION}/report`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2500);

  const before = await page.evaluate(() => ({
    hasButton: Boolean(document.querySelector('.sample-play')),
    buttonText: document.querySelector('.sample-play')?.textContent?.trim() || null,
    hasAvatar: Boolean(document.querySelector('.sample-avatar .digital-human')),
    // 初始状态下示范回答应该是**看不见**的，这里看的是占位提示
    captionLength: document.querySelector('.sample-text')?.textContent?.length || 0,
    hiddenPlaceholder: Boolean(document.querySelector('.sample-text.sample-hidden')),
  }));
  console.log('点击前:', JSON.stringify(before));

  // 结构检查：小人应在按钮左侧，字幕应在两者下方
  const layout = await page.evaluate(() => {
    const box = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
    };
    return { avatar: box('.sample-avatar'), button: box('.sample-play'), caption: box('.sample-text') };
  });
  if (layout.avatar && layout.button && layout.caption) {
    console.log('布局检查:', JSON.stringify({
      小人: layout.avatar, 按钮: layout.button, 字幕: layout.caption,
      小人在按钮左侧: layout.avatar.x + layout.avatar.w <= layout.button.x + 1,
      字幕在两者下方: layout.caption.y >= Math.max(layout.avatar.y, layout.button.y),
    }));
  }

  if (!before.hasButton) {
    console.error('报告页没找到「让面试官念一遍」按钮 —— 可能是报告还没评分完成，或这个会话没有示范回答');
    await page.screenshot({ path: `${OUT}/11-report-sample-answer.png` });
    process.exit(2);
  }

  await page.click('.sample-play');
  // 后端每次点击都要真实合成一遍语音（约 3-5 秒），这里等它进入播放态
  await page.waitForTimeout(6000);
  const afterClick = await page.evaluate(() => ({
    buttonText: document.querySelector('.sample-play')?.textContent?.trim() || null,
    disabled: document.querySelector('.sample-play')?.disabled ?? null,
    errorText: document.querySelector('.sample-note.error')?.textContent?.trim() || null,
    notice: document.querySelector('.sample-note:not(.error)')?.textContent?.trim() || null,
  }));
  console.log('点击后:', JSON.stringify(afterClick));

  const frames = [];
  const captionLengths = [];
  let sawSpeaking = false;
  let shot = false;
  for (let i = 0; i < 120; i += 1) {
    const snapshot = await page.evaluate(() => {
      const img = document.querySelector('.sample-avatar img.dh-frame');
      return {
        frame: (img?.getAttribute('src') || '').split('/').pop() || null,
        playing: Boolean(document.querySelector('.sample-play.playing')),
        caption: document.querySelector('.sample-text')?.textContent?.length || 0,
      };
    });
    if (snapshot.frame) frames.push(snapshot.frame);
    captionLengths.push(snapshot.caption);
    if (snapshot.playing && !sawSpeaking) sawSpeaking = true;
    if (snapshot.playing && !shot) {
      shot = true;
      await page.screenshot({ path: `${OUT}/11-report-sample-answer.png` });
    }
    if (!snapshot.playing && frames.length > 8) break;
    await page.waitForTimeout(150);
  }

  console.log('播放中采样:', JSON.stringify({
    samples: frames.length,
    sawSpeaking,
    distinctFrames: [...new Set(frames)],
    captionFrom: captionLengths[0],
    captionMid: captionLengths[Math.floor(captionLengths.length / 2)],
    captionEnd: captionLengths[captionLengths.length - 1],
  }));
  if (shot) console.log(`截图：${OUT}/11-report-sample-answer.png`);
} finally {
  await browser.close();
}
