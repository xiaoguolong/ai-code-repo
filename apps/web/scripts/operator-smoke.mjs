import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const BASE = process.env.WEB_BASE ?? 'http://127.0.0.1:5173';
const LOG_DIR = resolve(process.env.LOG_DIR ?? '../../logs/week-29');
mkdirSync(LOG_DIR, { recursive: true });

const checks = [];
const record = (name, ok, detail = '') => {
  checks.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` (${detail})` : ''}`);
};

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();

await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
await page.fill('input[autocomplete="username"]', 'operator');
await page.fill('input[autocomplete="current-password"]', 'operator123');
await page.click('button[type="submit"]');
await page.waitForURL(`${BASE}/`, { timeout: 20000 });
record('operator 登录', true);

// 知识库问答
await page.goto(`${BASE}/agents/knowledge-qa`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('text=运行参数', { timeout: 15000 });
await page.waitForTimeout(1200);
await page.fill('textarea[placeholder^="例如：糖尿病"]', '糖尿病患者的随访频率是多少？');
await page.click('button:has-text("运行 Agent")');
await page.waitForSelector('text=查看执行详情', { timeout: 180000 });
const qaText = await page.locator('.ant-card').last().innerText();
record('knowledge-qa 运行完成', qaText.includes('完成') || qaText.includes('待审'), qaText.slice(0, 80));
await page.screenshot({ path: `${LOG_DIR}/smoke-07-knowledge-qa.png`, fullPage: true });

// 随访
await page.goto(`${BASE}/agents/followup`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('text=运行参数', { timeout: 15000 });
await page.fill('input[placeholder="例如：P002"]', 'P002');
await page.click('button:has-text("运行 Agent")');
await page.waitForSelector('text=查看执行详情', { timeout: 180000 });
await page.screenshot({ path: `${LOG_DIR}/smoke-08-followup.png`, fullPage: true });
record('followup 运行完成', true);

// 审批拒绝（第一条待审）
await page.goto(`${BASE}/approvals`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('.ant-table-tbody tr.ant-table-row', { timeout: 15000 });
await page.locator('.ant-table-tbody tr.ant-table-row').first().click();
await page.waitForSelector('.ant-modal', { state: 'visible', timeout: 10000 });
await page.waitForTimeout(500);
await page.getByRole('button', { name: /拒\s*绝/ }).first().click();
await page.waitForTimeout(500);
await page.locator('.ant-modal').last().getByRole('button', { name: /确\s*定/ }).click();
await page.waitForTimeout(1500);
record('审批拒绝操作完成', true);
await page.screenshot({ path: `${LOG_DIR}/smoke-09-approval-rejected.png`, fullPage: true });

await context.close();
await browser.close();
const failed = checks.filter((ok) => !ok).length;
console.log(`RESULT PASS=${checks.length - failed} FAIL=${failed}`);
process.exitCode = failed === 0 ? 0 : 1;
