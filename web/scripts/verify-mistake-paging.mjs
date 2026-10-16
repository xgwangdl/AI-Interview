/**
 * 错题本分页页的真浏览器验证（配合 scripts/verify-mistake-paging.ps1 使用）。
 *
 * 脚本**自己造数据不划算**：25 条错题由 PowerShell 那边写进库（用完删掉），
 * 这里只负责看界面是不是真的分页、能不能按岗位筛、重练能不能点通。
 *
 *     node web/scripts/verify-mistake-paging.mjs
 *
 * 需要后端（8088）与前端（5173）都在跑，且库里已有 `vfy-pg-*` 那批错题。
 * 退出时打印 `CREATED_SESSION=<id>`，交给调用方清理。
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
const EXPECTED_TOTAL = Number(process.env.EXPECTED_TOTAL || 25);
const PAGE_SIZE = 20;

mkdirSync(OUT, { recursive: true });
let failures = 0;

function check(label, condition, detail) {
  console.log(`  ${condition ? 'PASS' : 'FAIL'} ${label} ${detail ?? ''}`);
  if (!condition) failures += 1;
}

const browser = await chromium.launch({ executablePath: CHROME, args: ['--hide-scrollbars'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1200 } });
page.on('pageerror', (error) => console.log(`  [pageerror] ${error.message}`));

async function shot(name) {
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  [shot] ${name}`);
}

let createdSessionId = null;

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[placeholder="2-80 位，支持中文"]', USER);
  await page.fill('input[placeholder="输入密码"]', CODE);
  await page.click('button:has-text("安全登录")');
  await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 20000 });

  // 1) 总览页：只放 5 条 + 一行"查看全部 N 道"
  await page.waitForSelector('.mistake-section', { timeout: 20000 });
  const overviewRows = await page.locator('.mistake-row').count();
  const allLink = page.locator('.mistake-all-row button');
  check('总览页只保留 5 条', overviewRows === 5, `rows=${overviewRows}`);
  check('有"查看全部 N 道"入口', await allLink.count() === 1);
  const linkText = (await allLink.innerText()).replace(/\s+/g, ' ').trim();
  check('入口写的是真实总数', linkText.includes(String(EXPECTED_TOTAL)), linkText);
  await shot('mistake-paging-dashboard');

  // 2) 进错题本页：第一页 20 条
  await allLink.click();
  await page.waitForURL((url) => url.pathname === '/mistakes', { timeout: 15000 });
  await page.waitForSelector('.mistake-row', { timeout: 15000 });
  await page.waitForTimeout(500);
  const firstPageRows = await page.locator('.mistake-row').count();
  check('第一页 20 条', firstPageRows === PAGE_SIZE, `rows=${firstPageRows}`);
  check('出现分页控件', await page.locator('.mistake-pager .el-pagination').count() === 1);
  check('底下一行写清本页与总数', (await page.locator('.mistake-more').innerText()).includes(String(EXPECTED_TOTAL)),
    (await page.locator('.mistake-more').innerText()).replace(/\s+/g, ' '));
  await shot('mistake-paging-page1');

  // 3) 翻到第二页：只剩 5 条，且内容与第一页不同
  const firstPageFirstText = await page.locator('.mistake-row').first().innerText();
  await page.locator('.mistake-pager .el-pager li').last().click();
  await page.waitForTimeout(900);
  const secondPageRows = await page.locator('.mistake-row').count();
  const secondPageFirstText = await page.locator('.mistake-row').first().innerText();
  check('第二页只剩 5 条', secondPageRows === EXPECTED_TOTAL - PAGE_SIZE, `rows=${secondPageRows}`);
  check('第二页不是第一页的重复', secondPageFirstText !== firstPageFirstText);
  await shot('mistake-paging-page2');

  // 4) 按岗位筛选：换筛选要回到第一页
  const options = await page.locator('.role-filter').click().then(async () => {
    await page.waitForTimeout(400);
    return page.locator('.el-select-dropdown__item').allInnerTexts();
  });
  const roleOption = options.find((text) => text.includes('前端工程师'));
  check('筛选下拉里有岗位与数量', Boolean(roleOption), options.join(' / '));
  await page.locator('.el-select-dropdown__item', { hasText: '前端工程师' }).first().click();
  await page.waitForTimeout(900);
  const filteredRows = await page.locator('.mistake-row').count();
  const pagerGone = await page.locator('.mistake-pager .el-pagination').count();
  check('筛选后条数变成该岗位的错题数', filteredRows === 10, `rows=${filteredRows}`);
  check('筛选后只有一页就不显示分页控件', pagerGone === 0);
  await shot('mistake-paging-filtered');

  // 5) 在这一页直接重练这道题
  await page.locator('.mistake-row').first().locator('button:has-text("重练这道题")').click();
  await page.waitForURL((url) => url.pathname.includes('/practice/device-check'), { timeout: 20000 });
  createdSessionId = new URL(page.url()).searchParams.get('id');
  check('重练能点通，落到设备检测页', Boolean(createdSessionId), page.url());
  await shot('mistake-paging-retry');
} catch (error) {
  console.log(`\n失败：${error.message}`);
  await shot('mistake-paging-failed');
  failures += 1;
} finally {
  await browser.close();
}

console.log(`\n界面检查：${failures === 0 ? '全部通过' : failures + ' 项失败'}`);
console.log(`CREATED_SESSION=${createdSessionId || ''}`);
process.exitCode = failures === 0 ? 0 : 1;
