import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const BASE = process.env.WEB_BASE ?? 'http://127.0.0.1:5173';
const LOG_DIR = resolve(process.env.LOG_DIR ?? '../../logs/week-29');
mkdirSync(LOG_DIR, { recursive: true });

const errors = [];
const checks = [];
const record = (name, ok, detail = '') => {
  checks.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` (${detail})` : ''}`);
};

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(`console: ${msg.text()}`);
});
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));

async function shot(name) {
  await page.screenshot({ path: `${LOG_DIR}/${name}.png`, fullPage: true });
}

async function login(target, username, password) {
  await target.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await target.fill('input[autocomplete="username"]', username);
  await target.fill('input[autocomplete="current-password"]', password);
  await target.click('button[type="submit"]');
  await target.waitForURL(`${BASE}/`, { timeout: 20000 });
}

// 1) admin 登录 + 工作台
await login(page, 'admin', 'admin123');
await page.waitForSelector('text=待审审批', { timeout: 15000 });
await page.waitForTimeout(1500);
record('admin 登录并进入工作台', true);
await shot('smoke-01-dashboard');

// 2) 患者分析（P002 HIGH）；SMOKE_SKIP_AGENT=1 时跳过（避免重复造待审数据）
if (process.env.SMOKE_SKIP_AGENT === '1') {
  record('patient-analysis 运行（跳过，沿用上一轮证据）', true);
} else {
  await page.goto(`${BASE}/agents/patient-analysis`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('text=运行参数', { timeout: 15000 });
  await page.fill('input[placeholder="例如：P002"]', 'P002');
  await page.click('button:has-text("运行 Agent")');
  await page.waitForSelector('text=查看执行详情', { timeout: 180000 });
  const resultText = await page.locator('.ant-card').last().innerText();
  record('patient-analysis 运行完成', resultText.includes('完成') || resultText.includes('待审'), resultText.slice(0, 80));
  await shot('smoke-02-agent-result');
}

// 3) 审批页
await page.goto(`${BASE}/approvals`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('text=待办', { timeout: 15000 });
await page.waitForTimeout(1500);
record('审批页加载', true);
await shot('smoke-03-approvals');

// 4) 知识库页
await page.goto(`${BASE}/knowledge`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('text=租户绑定', { timeout: 15000 });
await page
  .waitForFunction(() => document.body.innerText.includes('医疗随访规范库'), { timeout: 15000 })
  .catch(() => undefined);
const knowledgeText = await page.locator('body').innerText();
record('知识库绑定加载', knowledgeText.includes('医疗随访规范库'), knowledgeText.includes('MISSING') ? 'MISSING' : 'ACTIVE');
await shot('smoke-04-knowledge');

// 5) 用户管理
await page.goto(`${BASE}/users`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('text=用户管理', { timeout: 15000 });
await page.waitForTimeout(1200);
record('用户管理加载', true);
await shot('smoke-05-users');

// 6) viewer 只读 + 路由拦截
const viewerContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const viewer = await viewerContext.newPage();
await login(viewer, 'viewer', 'viewer123');
await viewer.waitForSelector('text=工作台', { timeout: 15000 });
const viewerMenu = await viewer.locator('text=用户管理').count();
record('viewer 菜单不含用户管理', viewerMenu === 0);
await viewer.goto(`${BASE}/agents/patient-analysis`, { waitUntil: 'domcontentloaded' });
await viewer.waitForURL(`${BASE}/`, { timeout: 15000 });
record('viewer 直访 Agent 路由被拦截', true);
await viewer.goto(`${BASE}/ent/knowledge`, { waitUntil: 'domcontentloaded' });
await viewer.waitForURL(`${BASE}/`, { timeout: 15000 });
record('viewer 直访企业知识库被拦截', true);
await viewer.screenshot({ path: `${LOG_DIR}/smoke-06-viewer.png`, fullPage: true });

await viewerContext.close();
await context.close();
await browser.close();

const failed = checks.filter((item) => !item.ok).length + errors.length;
console.log(`RESULT PASS=${checks.filter((i) => i.ok).length} FAIL=${failed}`);
if (errors.length) {
  console.log('CONSOLE ERRORS:');
  for (const error of errors) {
    console.log(`- ${error}`);
  }
}
process.exitCode = failed === 0 ? 0 : 1;
