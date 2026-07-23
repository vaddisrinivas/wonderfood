#!/usr/bin/env node
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createRequire } from 'node:module';

const root = process.cwd();
const baseUrl = process.env.LIFEOS_WEB_BASE_URL || 'http://127.0.0.1:8094';
const outDir = join(root, 'app', 'build', 'evidence', 'web-product-smoke');
const chromeCandidates = [
  process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/Applications/Chromium.app/Contents/MacOS/Chromium',
].filter(Boolean);

function requirePlaywright() {
  const moduleDirs = [
    process.env.PLAYWRIGHT_NODE_MODULES,
    process.env.CODEX_PRIMARY_NODE_MODULES,
    '/Users/srinivasvaddi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules',
  ].filter(Boolean);
  for (const dir of moduleDirs) {
    try {
      return createRequire(join(dir, 'package.json'))('playwright');
    } catch {
      // try next
    }
  }
  try {
    return createRequire(join(root, 'package.json'))('playwright');
  } catch {
    throw new Error('Playwright not found. Set PLAYWRIGHT_NODE_MODULES to a node_modules containing playwright, or install it for local QA.');
  }
}

function chromeExecutable() {
  return chromeCandidates.find((candidate) => candidate && existsSync(candidate));
}

const routes = [
  { name: 'home', path: '/', must: ['LIFEOS / HOME', 'Food'] },
  { name: 'food', path: '/food', must: ['Food workspace', 'Meals', 'Kitchen', 'Shopping'] },
  { name: 'record-green-dal', path: '/record/meal-green-dal', must: ['Nutrition profile', 'Ingredients and availability', 'Connected records'] },
  { name: 'chat', path: '/chat', must: ['Talk to your life', 'Chat cites sources'] },
  { name: 'config', path: '/config', must: ['Active package contract', 'COLLECTIONS', 'MCP CONTRACT'] },
  { name: 'settings', path: '/settings', must: ['AI providers', 'Data sources', 'LifeOS behavior'] },
];

const { chromium } = requirePlaywright();
const executablePath = chromeExecutable();
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({
  headless: true,
  ...(executablePath ? { executablePath } : {}),
});

const page = await browser.newPage({ viewport: { width: 1500, height: 1100 }, deviceScaleFactor: 1 });
const results = [];

for (const route of routes) {
  const url = `${baseUrl}${route.path}`;
  const result = {
    name: route.name,
    url,
    ok: false,
    missing: [],
    screenshot: join(outDir, `${route.name}.png`),
    consoleErrors: [],
  };
  const consoleErrors = [];
  page.removeAllListeners('console');
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 20000 });
    const text = await page.locator('body').innerText({ timeout: 8000 });
    result.missing = route.must.filter((needle) => !text.includes(needle));
    result.consoleErrors = consoleErrors;
    await page.screenshot({ path: result.screenshot, fullPage: true });
    result.ok = result.missing.length === 0 && consoleErrors.length === 0;
  } catch (error) {
    result.error = error instanceof Error ? error.message : String(error);
  }
  results.push(result);
}

await browser.close();

const evidence = {
  pass: results.every((result) => result.ok),
  baseUrl,
  checkedAt: new Date().toISOString(),
  results,
};

const outPath = join(outDir, 'web-product-smoke.json');
writeFileSync(outPath, JSON.stringify(evidence, null, 2), 'utf-8');

if (!evidence.pass) {
  console.error(`FAIL ${outPath}`);
  console.error(JSON.stringify(evidence, null, 2));
  process.exit(1);
}

console.log(`PASS ${outPath}`);
