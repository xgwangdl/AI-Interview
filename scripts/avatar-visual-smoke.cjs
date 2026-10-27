const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { chromium } = require('../web/node_modules/playwright');

const baseUrl = process.env.BASE_URL || 'http://localhost:5173';
const userName = process.env.SMOKE_USER || 'smoke';
const userCode = process.env.SMOKE_CODE || '123456';
const chromePath = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
].filter(Boolean).find((candidate) => fs.existsSync(candidate));

if (!chromePath) throw new Error('没有找到 Chrome/Edge，可通过 CHROME_PATH 指定浏览器路径。');

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: chromePath });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const errors = [];

  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource')) {
      errors.push(message.text());
    }
  });

  try {
    await page.goto(`${baseUrl}/login?redirect=/language`, { waitUntil: 'domcontentloaded' });
    const login = await page.evaluate(async ({ name, code }) => {
      const response = await fetch('/login', {
        method: 'POST',
        body: new URLSearchParams({ name, code }),
      });
      return response.json();
    }, { name: userName, code: userCode });
    if (login.code !== 200 || !login.data?.token) {
      throw new Error(`冒烟账号登录失败：${JSON.stringify(login)}`);
    }
    await page.evaluate((data) => {
      localStorage.setItem('token', data.token);
      localStorage.setItem('userId', String(data.userId));
      localStorage.setItem('userName', data.userName || 'smoke');
    }, login.data);

    await page.goto(`${baseUrl}/language`, { waitUntil: 'networkidle' });
    await page.locator('.language-page').waitFor();
    if (await page.locator('.history-row').count()) {
      await page.locator('.history-row').first().click();
    } else {
      await page.locator('.scenario-card').first().click();
      await page.locator('.start-button').click();
    }
    await page.waitForURL(/\/language\/[^/]+$/, { timeout: 20000 });

    const frame = page.locator('.dh-frame').first();
    await frame.waitFor({ state: 'visible', timeout: 20000 });
    const readFrame = () => frame.evaluate((element) => {
      const style = getComputedStyle(element);
      return {
        src: element.getAttribute('src'),
        animationName: style.animationName,
        transform: style.transform,
        translate: style.translate,
        hostClass: element.closest('.digital-human')?.className,
      };
    });

    const idle = await readFrame();
    if (!idle.src?.startsWith('/assets/avatar/english-coach/mouth-closed.png?v=')) {
      throw new Error(`待机帧不是新版闭嘴素材：${idle.src}`);
    }
    if (idle.animationName !== 'none' || idle.translate !== 'none') {
      throw new Error(`英语外教仍在整图运动：${JSON.stringify(idle)}`);
    }
    if (!idle.hostClass.includes('ambient-motion-off')) {
      throw new Error(`英语外教缺少 ambient-motion-off 状态：${idle.hostClass}`);
    }

    const idleShot = path.join(os.tmpdir(), 'english-coach-idle.png');
    await page.screenshot({ path: idleShot });

    await page.locator('button.replay').first().click();
    await page.waitForFunction(() => (
      /mouth-(normal|e|o|a|m|smile)/.test(document.querySelector('.dh-frame')?.getAttribute('src') || '')
    ), null, { timeout: 15000 });
    const speaking = await readFrame();
    if (speaking.animationName !== 'none' || speaking.translate !== 'none') {
      throw new Error(`英语外教说话时仍在整图运动：${JSON.stringify(speaking)}`);
    }
    const speakingShot = path.join(os.tmpdir(), 'english-coach-speaking.png');
    await page.screenshot({ path: speakingShot });

    if (errors.length) throw new Error(`页面脚本错误：${errors.join(' | ')}`);
    console.log('PASS 英语外教待机与说话使用统一机位素材');
    console.log('PASS 英语外教关闭整图浮动、摆动与眨眼');
    console.log(`IDLE ${idleShot}`);
    console.log(`SPEAKING ${speakingShot}`);
    console.log(JSON.stringify({ idle, speaking }, null, 2));
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(`FAIL ${error.stack || error}`);
  process.exitCode = 1;
});
