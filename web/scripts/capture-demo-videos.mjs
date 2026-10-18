/**
 * 录制 README 当前版本演示视频。
 *
 * - 使用真实前端和演示账号；
 * - 用 API 创建临时练习，并通过本地数据库写入固定演示评分，避免真实 AI 费用；
 * - 面试页拦截 TTS 音频请求，保留真实界面但使用无音频降级；
 * - 录制完成后删除临时练习。
 *
 *   docker compose up -d postgres redis
 *   java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar
 *   npm --prefix web run dev
 *   node web/scripts/capture-demo-videos.mjs
 */
import { chromium } from 'playwright';
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../..');
const docsDir = resolve(repoRoot, 'docs');
const imagesDir = resolve(docsDir, 'images');
const baseUrl = process.env.BASE_URL || 'http://localhost:5173';
const apiUrl = process.env.API_URL || 'http://localhost:8088';
const chromePath = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const ffmpegPath = process.env.FFMPEG_PATH || 'ffmpeg';
const demoUser = process.env.DEMO_USER || 'smoke';
const demoCode = process.env.DEMO_CODE || '123456';

if (!existsSync(chromePath)) throw new Error(`Chrome not found: ${chromePath}`);
await mkdir(imagesDir, { recursive: true });
const videoTemp = await mkdtemp(resolve(repoRoot, 'data', 'demo-video-'));

async function login() {
  const response = await fetch(`${apiUrl}/login`, {
    method: 'POST',
    body: new URLSearchParams({ name: demoUser, code: demoCode }),
  });
  const body = await response.json();
  if (!response.ok || !body.data?.token) throw new Error('Demo login failed');
  return body.data;
}

async function api(path, token, options = {}) {
  const response = await fetch(`${apiUrl}/api/v1${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) throw new Error(`${options.method || 'GET'} ${path}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}

const auth = await login();

async function createDemoPractice() {
  const session = await api('/practice-sessions', auth.token, {
    method: 'POST',
    body: JSON.stringify({
      role: 'Java 后端工程师',
      company: '星云科技',
      jobDescription: '负责订单平台、缓存与高并发链路设计，重视工程质量和业务结果。',
      resumeName: 'demo-resume.pdf',
      goalId: null,
      interviewType: 'technical',
      difficulty: 'standard',
      interviewerStyle: 'balanced',
      questionCount: 1,
      durationMinutes: 20,
      followUpEnabled: true,
      mode: 'per_question',
      questions: [{
        id: 'demo-q-1',
        text: '请用 STAR 结构讲一个你主导或深度参与的项目：背景、任务、你的行动和结果。',
        category: '项目设计',
      }],
    }),
  });
  const answer = await api(`/practice-sessions/${session.id}/answers`, auth.token, {
    method: 'POST',
    body: JSON.stringify({
      questionIndex: 0,
      questionId: 'demo-q-1',
      durationSeconds: 52,
      startedAtOffsetSeconds: 0,
    }),
  });
  return { session, answer };
}

function seedDemoReport({ session, answer }) {
  const transcript = '我负责订单查询链路的重构。先梳理慢查询和调用链，再拆分高频查询、补充缓存并统一结果校验，最后和测试一起做灰度验证。上线后接口稳定性明显改善，也减少了重复排查。';
  const rewrite = '我主导了订单查询链路重构：先梳理慢查询与调用链，再拆分高频查询、补充缓存并统一结果校验，最后联合测试完成灰度验证。上线后接口稳定性明显改善，重复排查也明显减少。';
  const report = {
    modelName: 'demo-fixture',
    modelVersion: 'demo-fixture',
    promptVersion: 'demo-fixture',
    analyzedAt: new Date().toISOString(),
    rawResponse: '{"source":"local-demo-fixture"}',
    result: {
      overallScore: 84,
      scores: {
        relevance: 88,
        completeness: 82,
        professionalism: 86,
        starStructure: 81,
        logic: 85,
      },
      delivery: {
        speechRateCharactersPerMinute: 218,
        pauseCount: 2,
        totalPauseSeconds: 3.2,
        fillerWordCount: 1,
      },
      improvementSuggestions: [
        '先交代项目目标与个人职责，再展开关键行动，STAR 会更完整。',
        '结果部分可以补充真实可披露的业务指标，避免只描述“稳定性改善”。',
        '结尾加一句复盘，说明如果重做一次会优先优化什么。',
      ],
      sampleAnswer: '在上一段项目中，我负责订单查询链路重构。背景是高峰期查询慢且重复排查多，我的任务是降低接口波动并提升可维护性。我先梳理慢查询和调用链，再拆分高频查询、补充缓存并统一结果校验，最后联合测试完成灰度验证。上线后接口稳定性和排查效率都有改善。复盘来看，下一步应更早把监控指标纳入设计。',
      evidence: [{
        dimension: 'starStructure',
        quote: '先梳理慢查询和调用链，再拆分高频查询、补充缓存并统一结果校验',
        startSeconds: 8,
        endSeconds: 21,
      }],
    },
  };
  const encode = (value) => Buffer.from(value, 'utf8').toString('base64');
  const sql = `
    update practice_answers
       set transcript_text = convert_from(decode('${encode(transcript)}', 'base64'), 'UTF8'),
           transcript_status = 'completed',
           evaluation_status = 'completed',
           evaluation_json = convert_from(decode('${encode(JSON.stringify(report))}', 'base64'), 'UTF8'),
           rewrite_text = convert_from(decode('${encode(rewrite)}', 'base64'), 'UTF8'),
           rewrite_source = 'ai',
           rewrite_at = now()
     where id = '${answer.id}';
    update practice_sessions set status = 'completed', completed_at = now()
     where id = '${session.id}';
  `;
  const result = spawnSync('docker', [
    'exec', 'light-interview-postgres', 'psql', '-U', 'postgres', '-d', 'interview', '-v', 'ON_ERROR_STOP=1', '-c', sql,
  ], { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || 'Unable to seed demo report');
}

async function findReadyReportSession() {
  const sessions = await api('/practice-sessions', auth.token);
  for (const session of sessions) {
    if (session.status !== 'completed') continue;
    try {
      const report = await api(`/practice-sessions/${session.id}/report`, auth.token);
      if (report.status === 'ready') return session.id;
    } catch {
      // Try the next completed session.
    }
  }
  return null;
}

async function findCodingSession() {
  const sessions = await api('/coding-sessions', auth.token);
  return sessions.find((item) => item.questions?.length) || null;
}

async function setCaption(page, text) {
  await page.evaluate((caption) => {
    let element = document.querySelector('#demo-caption');
    if (!element) {
      element = document.createElement('div');
      element.id = 'demo-caption';
      document.body.appendChild(element);
    }
    element.textContent = caption;
  }, text);
}

async function decorate(page, caption) {
  await page.addStyleTag({ content: `
    #demo-caption {
      position: fixed;
      z-index: 99999;
      left: 50%;
      bottom: 24px;
      max-width: min(760px, calc(100vw - 48px));
      transform: translateX(-50%);
      padding: 10px 18px;
      border-radius: 7px;
      background: rgba(24, 38, 31, .9);
      box-shadow: 0 8px 28px rgba(15, 27, 20, .2);
      color: #f6fbf8;
      font: 600 15px/1.5 'Microsoft YaHei UI', sans-serif;
      letter-spacing: .02em;
      text-align: center;
      pointer-events: none;
    }
  ` });
  await setCaption(page, caption);
}

async function neutralizeCamera(page) {
  await page.addStyleTag({ content: `
    .self-preview video, .preview-frame video { visibility: hidden !important; }
    .self-preview, .preview-frame:has(video) { background: #26372f !important; }
    .self-preview::after {
      position: absolute;
      inset: 0;
      z-index: 1;
      display: grid;
      place-content: center;
      color: #c0d1c6;
      font-size: 12px;
      content: "候选人画面示意";
    }
    .preview-frame::after {
      position: absolute;
      inset: 0;
      display: grid;
      place-content: center;
      color: #b9cabd;
      font-size: 20px;
      font-weight: 600;
      content: "摄像头预览示意";
    }
    .recording-indicator, .preview-live, .preview-corner { z-index: 2; }
  ` });
}

async function neutralizeReportPlayer(page) {
  await page.addStyleTag({ content: `
    .player-frame { background: #26372f !important; }
    .player-frame video, .player-frame .player-empty { display: none !important; }
    .player-frame::after {
      position: absolute;
      inset: 0;
      z-index: 2;
      display: grid;
      place-content: center;
      color: #c4d4ca;
      font: 600 18px/1.6 'Microsoft YaHei UI', sans-serif;
      content: "视频回放区域\\A演示视频中已省略";
      white-space: pre;
      text-align: center;
    }
    .player-label { z-index: 3; }
  ` });
}

async function record(name, action, authenticated = true) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
    recordVideo: { dir: videoTemp, size: { width: 1280, height: 720 } },
    permissions: ['camera', 'microphone'],
  });
  if (authenticated) {
    await context.addInitScript(({ token, userId, userName }) => {
      localStorage.setItem('token', token);
      localStorage.setItem('userId', String(userId));
      localStorage.setItem('userName', userName);
    }, auth);
  }
  const page = await context.newPage();
  const video = page.video();
  try {
    await action(page);
  } finally {
    await context.close();
  }
  const source = await video.path();
  const output = resolve(docsDir, `${name}.mp4`);
  const result = spawnSync(ffmpegPath, [
    '-y', '-i', source,
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '23',
    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-an',
    output,
  ], { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || `ffmpeg failed for ${name}`);
  console.log(`VIDEO ${output}`);
}

const demoPractice = await createDemoPractice();
seedDemoReport(demoPractice);
const readyReportId = await findReadyReportSession();
const coding = await findCodingSession();
const demoReportId = demoPractice.session.id || readyReportId;

const browser = await chromium.launch({
  executablePath: chromePath,
  args: [
    '--hide-scrollbars',
    '--use-fake-device-for-media-stream',
    '--use-fake-ui-for-media-stream',
    '--autoplay-policy=no-user-gesture-required',
  ],
});

try {
  await record('demo-tour', async (page) => {
    await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' });
    await decorate(page, '从岗位、JD 与简历开始一次训练');
    await page.waitForTimeout(2200);
    await page.locator('.login-form input').nth(0).fill(demoUser);
    await page.locator('.login-form input').nth(1).fill(demoCode);
    await page.getByRole('button', { name: /安全登录/ }).click();
    await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 });
    await page.waitForTimeout(2800);
    await setCaption(page, '目标、今日训练与成长趋势');
    await page.screenshot({ path: resolve(imagesDir, 'demo-tour-poster.png') });
    await page.waitForTimeout(2400);

    await page.goto(`${baseUrl}/practice/setup`, { waitUntil: 'networkidle' });
    await decorate(page, '按岗位与 JD 配置模拟面试');
    await page.waitForTimeout(4200);

    if (coding) {
      await page.goto(`${baseUrl}/coding/${coding.id}`, { waitUntil: 'networkidle' });
      await decorate(page, '笔试支持网页 IDE 与真实用例判题');
      await page.waitForTimeout(4500);
    }

    await page.goto(`${baseUrl}/practice/${demoReportId}/report`, { waitUntil: 'networkidle' });
    await neutralizeReportPlayer(page);
    await decorate(page, '逐题评分、视频证据和可执行建议');
    await page.waitForTimeout(4200);
  }, false);

  await record('demo-interview', async (page) => {
    await page.route(`**/api/v1/practice-sessions/${demoPractice.session.id}/questions/*/narration`, (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        provider: 'demo',
        state: 'speaking',
        text: '请用 STAR 结构讲一个你主导或深度参与的项目：背景、任务、你的行动和结果。',
        interviewerStyle: 'balanced',
        audioAvailable: false,
        audioUrl: null,
        voice: null,
        truncated: false,
      }),
    }));
    await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
    await page.goto(`${baseUrl}/practice/device-check?id=${demoPractice.session.id}`, { waitUntil: 'networkidle' });
    await decorate(page, '摄像头与麦克风检测');
    await neutralizeCamera(page);
    await page.waitForTimeout(1200);
    await page.getByRole('button', { name: '开启设备检测' }).click();
    const enter = page.getByRole('button', { name: /进入面试/ });
    await enter.waitFor({ state: 'visible', timeout: 15000 });
    for (let index = 0; index < 20 && !(await enter.isEnabled()); index += 1) await page.waitForTimeout(400);
    await enter.click();
    await page.waitForURL(/\/practice\/.+\/session/, { timeout: 15000 });
    await neutralizeCamera(page);
    await page.waitForTimeout(1600);
    await setCaption(page, '面试官逐题提问，回答在浏览器中录制');
    await page.screenshot({ path: resolve(imagesDir, 'demo-interview-poster.png') });
    await page.waitForTimeout(3500);
    await page.getByRole('button', { name: '开始回答' }).click();
    await page.waitForTimeout(4200);
    await setCaption(page, '每题独立录制、暂停和保存');
  });

  await record('demo-report', async (page) => {
    await page.goto(`${baseUrl}/practice/${demoReportId}/report`, { waitUntil: 'networkidle' });
    await neutralizeReportPlayer(page);
    await decorate(page, '逐题报告：分数、证据与改进建议');
    await page.waitForTimeout(2600);
    await page.screenshot({ path: resolve(imagesDir, 'demo-report-poster.png') });
    await page.locator('.rewrite-panel').scrollIntoViewIfNeeded();
    await setCaption(page, '回答改写只优化表达，不新增经历和数字');
    await page.waitForTimeout(3800);
    const edit = page.getByRole('button', { name: '编辑', exact: true });
    if (await edit.count()) {
      await edit.click();
      await page.waitForTimeout(2800);
    }
    await page.locator('.sample-panel').scrollIntoViewIfNeeded();
    await setCaption(page, '优秀回答示例与可定位视频证据');
    await page.waitForTimeout(3400);
  });
} finally {
  await browser.close();
  try {
    await api(`/practice-sessions/${demoPractice.session.id}`, auth.token, { method: 'DELETE' });
  } catch {
    // Cleanup is best-effort; the demo session has no user-facing data.
  }
  await rm(videoTemp, { recursive: true, force: true });
}
