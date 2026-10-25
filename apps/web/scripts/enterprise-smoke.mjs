import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const BASE = process.env.WEB_BASE ?? 'http://127.0.0.1:5173';
const LOG_DIR = resolve(process.env.LOG_DIR ?? '../../logs/week-30/enterprise');
mkdirSync(LOG_DIR, { recursive: true });

const NAME = `smoke-${Date.now()}`;
const DOC_NAME = `smoke-doc-${Date.now()}.txt`;
const DOC_CONTENT =
  '【冒烟】糖尿病患者随访频率：每 3 个月复查 HbA1c，血压目标低于 140/90 mmHg。本段仅用于 Week 30 企业页冒烟，随后删除。';

const checks = [];
const record = (name, ok, detail = '') => {
  checks.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` (${detail})` : ''}`);
};

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') {
    if (allowExpected401 && msg.text().includes('401')) {
      return;
    }
    errors.push(msg.text());
  }
});
page.on('pageerror', (err) => errors.push(err.message));

let token = '';
let baseId = null;
let allowExpected401 = false;

async function entApi(path, options = {}) {
  const response = await fetch(`${BASE}/ent${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { satoken: token } : {}),
      ...(options.headers ?? {})
    }
  });
  const text = await response.text();
  let body = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = {};
  }
  return { status: response.status, body };
}

async function poll(fn, timeout = 15000, interval = 400) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await fn()) {
      return true;
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, interval));
  }
  return false;
}

async function cleanup() {
  if (!token) {
    return;
  }
  const list = await entApi('/api/v1/knowledge-bases');
  const target = (list.body.data ?? []).find((item) => item.name === NAME);
  if (!target) {
    return;
  }
  const docs = await entApi(`/api/v1/knowledge-bases/${target.id}/documents`);
  for (const doc of docs.body.data ?? []) {
    await entApi(`/api/v1/knowledge-bases/${target.id}/documents/${doc.documentId}`, {
      method: 'DELETE'
    });
  }
  await entApi(`/api/v1/knowledge-bases/${target.id}`, { method: 'DELETE' });
  console.log(`CLEANUP removed ${NAME}`);
}

try {
  // 平台登录（企业页路由仅 admin）
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('input[autocomplete="username"]', 'admin');
  await page.fill('input[autocomplete="current-password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/`, { timeout: 20000 });

  await page.goto(`${BASE}/ent/knowledge`, { waitUntil: 'domcontentloaded' });
  // 等 Vue 挂载：要么出现企业登录卡，要么已登录出现知识库区
  await page.waitForFunction(
    () =>
      document.body.innerText.includes('企业登录') ||
      document.body.innerText.includes('医疗随访规范库'),
    { timeout: 20000 }
  );
  // 企业端可能已有/没有会话；没有则先登录
  if ((await page.getByRole('heading', { name: '企业登录' }).count()) > 0) {
    await page.fill('input[autocomplete="username"]', 'admin');
    await page.fill('input[autocomplete="current-password"]', 'admin123');
    await page.getByRole('button', { name: '登录企业端' }).click();
  }
  await page.waitForSelector('text=医疗随访规范库', { timeout: 20000 });
  token = await page.evaluate(() => sessionStorage.getItem('aicode_enterprise_token') ?? '');
  record('企业登录并加载知识库', Boolean(token));

  const before = await entApi('/api/v1/knowledge-bases');
  const beforeNames = (before.body.data ?? []).map((item) => item.name);
  record('既有知识库存在', beforeNames.includes('医疗随访规范库'));

  // 新建
  await page.getByRole('button', { name: /新\s*建/ }).click();
  const createModal = page.locator('.ant-modal:visible');
  await createModal.locator('input[placeholder^="例如：内分泌"]').fill(NAME);
  await createModal.locator('textarea').first().fill('Week 30 冒烟临时知识库');
  await createModal.getByRole('button', { name: /确\s*定/ }).click();
  const created = await poll(async () => {
    const list = await entApi('/api/v1/knowledge-bases');
    const found = (list.body.data ?? []).find((item) => item.name === NAME);
    baseId = found?.id ?? null;
    return Boolean(baseId);
  });
  record('新建知识库', created);

  // 入库
  await page.getByRole('button', { name: '入库文档' }).click();
  const ingestModal = page.locator('.ant-modal:visible');
  await ingestModal.locator('input[placeholder^="例如：糖尿病随访规范"]').fill(DOC_NAME);
  await ingestModal.locator('textarea').first().fill(DOC_CONTENT);
  await ingestModal.getByRole('button', { name: /确\s*定/ }).click();
  const ingested = await poll(async () => {
    const docs = await entApi(`/api/v1/knowledge-bases/${baseId}/documents`);
    return (docs.body.data ?? []).some((doc) => doc.name === DOC_NAME);
  });
  record('文本入库并出现在文档列表', ingested);

  // 删除文档
  const docRow = page.locator('.ant-table-tbody tr').filter({ hasText: DOC_NAME }).first();
  await docRow.getByRole('button', { name: /删\s*除/ }).click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /删\s*除/ }).click();
  const docDeleted = await poll(async () => {
    const docs = await entApi(`/api/v1/knowledge-bases/${baseId}/documents`);
    return (docs.body.data ?? []).length === 0;
  });
  record('删除文档', docDeleted);

  // 删除知识库
  const baseRow = page.locator('.ant-table-tbody tr').filter({ hasText: NAME }).first();
  await baseRow.getByRole('button', { name: /删\s*除/ }).click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /删\s*除/ }).click();
  const baseDeleted = await poll(async () => {
    const list = await entApi('/api/v1/knowledge-bases');
    return !(list.body.data ?? []).some((item) => item.name === NAME);
  });
  record('删除知识库', baseDeleted);

  const after = await entApi('/api/v1/knowledge-bases');
  const afterNames = (after.body.data ?? []).map((item) => item.name);
  record('既有知识库未被改动', afterNames.includes('医疗随访规范库'));

  await page.screenshot({ path: `${LOG_DIR}/enterprise-page.png`, fullPage: true });

  // 企业 401 隔离：企业 token 失效 → 企业登录卡；平台会话不受影响
  allowExpected401 = true;
  await page.evaluate(() => sessionStorage.setItem('aicode_enterprise_token', 'invalid-token'));
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => document.body.innerText.includes('企业登录'), {
    timeout: 15000
  });
  record('企业 401 只清企业会话', true);
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () =>
      document.body.innerText.includes('待审审批') ||
      document.body.innerText.includes('工作台'),
    { timeout: 15000 }
  );
  record('平台会话仍在', !page.url().includes('/login'));
} catch (error) {
  console.error(`FAIL 企业页冒烟异常：${error.message}`);
  checks.push(false);
} finally {
  await cleanup();
  await browser.close();
}

const failed = checks.filter((ok) => !ok).length + errors.length;
console.log(`RESULT PASS=${checks.filter((ok) => ok).length} FAIL=${failed}`);
if (errors.length) {
  console.log('CONSOLE ERRORS:');
  for (const error of errors) {
    console.log(`- ${error}`);
  }
}
process.exitCode = failed === 0 ? 0 : 1;
