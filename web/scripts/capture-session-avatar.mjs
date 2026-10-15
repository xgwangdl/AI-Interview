/**
 * 用真实浏览器 + 假摄像头走一遍「配置 → 设备检测 → 面试间」，给数字人截图。
 *
 *     node web/scripts/capture-session-avatar.mjs
 *
 * 为什么要假摄像头：面试间要求摄像头/麦克风已就绪，否则会跳回设备检测页。
 * Chrome 自带 --use-fake-device-for-media-stream / --use-fake-ui-for-media-stream，
 * 可以免去人工授权，让这段验证能自动化跑。
 *
 * 同样使用系统里的 Chrome（Playwright 自带的 Chromium 在内网常常下不动）。
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

mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  executablePath: CHROME,
  args: [
    '--hide-scrollbars',
    '--use-fake-device-for-media-stream',
    '--use-fake-ui-for-media-stream',
    '--autoplay-policy=no-user-gesture-required',
  ],
});
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  permissions: ['camera', 'microphone'],
});
const page = await context.newPage();
const log = (message) => console.log(`  ${message}`);

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
  await page.fill('input[placeholder="输入密码"]', CODE);
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });
  log('已登录');

  await page.goto(`${BASE}/practice/setup`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="Java 后端工程师"]', 'Java 后端工程师');
  await page.click('button:has-text("继续检测设备")');
  await page.waitForURL(/device-check/, { timeout: 20000 });
  log('已进入设备检测');

  const startCheck = page.locator('button:has-text("开启设备检测")');
  if (await startCheck.count()) {
    await startCheck.first().click();
    log('已触发设备检测');
  }
  const enter = page.locator('button:has-text("进入面试")');
  await enter.waitFor({ state: 'visible', timeout: 25000 });
  for (let i = 0; i < 20; i += 1) {
    if (await enter.first().isEnabled()) break;
    await page.waitForTimeout(700);
  }
  await enter.first().click();
  await page.waitForURL(/\/session/, { timeout: 25000 });
  log('已进入面试间');

  // 等配音请求与素材加载
  await page.waitForTimeout(6000);

  const probe = await page.evaluate(() => {
    const img = document.querySelector('.digital-human img.dh-frame');
    const box = img?.getBoundingClientRect();
    return {
      hasAvatar: Boolean(img),
      src: img?.getAttribute('src') || null,
      naturalSize: img ? `${img.naturalWidth}x${img.naturalHeight}` : null,
      renderedBox: box ? `${Math.round(box.width)}x${Math.round(box.height)}` : null,
      complete: img?.complete ?? null,
      statusText: document.querySelector('.interviewer-status')?.textContent?.trim() || null,
      roleText: document.querySelector('.interviewer-role')?.textContent?.trim() || null,
      replayVisible: Boolean(document.querySelector('.narration-replay')),
      barsActive: Boolean(document.querySelector('.voice-bars.active')),
    };
  });
  console.log('\n页面探测:', JSON.stringify(probe, null, 2));

  await page.screenshot({ path: `${OUT}/07-session-avatar.png` });
  log(`截图：${OUT}/07-session-avatar.png`);

  // 说话中抓帧：重播按钮是用户手势，一定能触发播放与音量分析。
  // 这里连续采样，统计到底出现过几张嘴型——只有真的在切换，才算"嘴型随音频变化"。
  if (probe.replayVisible) {
    // 注意：首次朗读可能已经在自动播放，而按钮在播放中是「暂停」。
    // 必须先等这一轮结束，再点，否则点下去是暂停、样本全是静止帧。
    for (let i = 0; i < 60; i += 1) {
      const speaking = await page.evaluate(() => Boolean(document.querySelector('.voice-bars.active')));
      if (!speaking) break;
      await page.waitForTimeout(200);
    }
    log('首次朗读已结束，开始重播采样');
    await page.click('.narration-replay');
    const frames = [];
    let shotWhileSpeaking = false;
    for (let i = 0; i < 40; i += 1) {
      const snapshot = await page.evaluate(() => {
        const img = document.querySelector('.digital-human img.dh-frame');
        return {
          frame: (img?.getAttribute('src') || '').split('/').pop()?.replace('.png', '') || null,
          speaking: Boolean(document.querySelector('.voice-bars.active')),
        };
      });
      if (snapshot.frame) frames.push(snapshot.frame);
      if (snapshot.speaking && !shotWhileSpeaking) {
        shotWhileSpeaking = true;
        await page.screenshot({ path: `${OUT}/08-session-avatar-speaking.png` });
      }
      if (!snapshot.speaking && frames.length > 6) break;
      await page.waitForTimeout(90);
    }
    const distinct = [...new Set(frames)];
    console.log('说话中的嘴型采样:', JSON.stringify({
      samples: frames.length,
      distinctFrames: distinct,
      shotWhileSpeaking,
    }, null, 2));
    const after = await page.evaluate(() => ({
      frame: (document.querySelector('.digital-human img.dh-frame')?.getAttribute('src') || '').split('/').pop(),
      statusText: document.querySelector('.interviewer-status')?.textContent?.trim() || null,
    }));
    console.log('结束后的状态:', JSON.stringify(after, null, 2));
    if (shotWhileSpeaking) log(`截图：${OUT}/08-session-avatar-speaking.png`);

    // 等朗读结束、切回「聆听」状态帧再截一张：说明标签的残留问题就出在这一类状态帧上
    for (let i = 0; i < 60; i += 1) {
      const speaking = await page.evaluate(() => Boolean(document.querySelector('.voice-bars.active')));
      if (!speaking) break;
      await page.waitForTimeout(200);
    }
    await page.waitForTimeout(800);
    const idle = await page.evaluate(() => ({
      frame: (document.querySelector('.digital-human img.dh-frame')?.getAttribute('src') || '').split('/').pop(),
      box: (() => {
        const el = document.querySelector('.digital-human img.dh-frame');
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
      })(),
    }));
    console.log('聆听态:', JSON.stringify(idle));
    await page.screenshot({ path: `${OUT}/09-session-avatar-listening.png` });
    log(`截图：${OUT}/09-session-avatar-listening.png`);
  }
} finally {
  await browser.close();
}
