import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const BASE = process.env.WEB_BASE ?? 'http://127.0.0.1:5173';
const LOG_DIR = resolve(process.env.LOG_DIR ?? '../../logs/week-29');
mkdirSync(LOG_DIR, { recursive: true });

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
await page.fill('input[autocomplete="username"]', 'operator');
await page.fill('input[autocomplete="current-password"]', 'operator123');
await page.click('button[type="submit"]');
await page.waitForURL(`${BASE}/`, { timeout: 20000 });

await page.goto(`${BASE}/approvals`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('.ant-table-tbody tr.ant-table-row', { timeout: 15000 });
await page.locator('.ant-table-tbody tr.ant-table-row').first().click();
await page.waitForSelector('.ant-modal', { state: 'visible', timeout: 10000 });
await page.waitForTimeout(500);
await page.getByRole('button', { name: /拒\s*绝/ }).first().click();
await page.waitForTimeout(500);
await page.locator('.ant-modal').last().getByRole('button', { name: /确\s*定/ }).click();
await page.waitForTimeout(1500);
await page.screenshot({ path: `${LOG_DIR}/smoke-09-approval-rejected.png`, fullPage: true });
console.log('PASS 审批拒绝流程（详情 → 拒绝 → 确定）');
await browser.close();
