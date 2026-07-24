#!/usr/bin/env node
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createRequire } from 'node:module';
import { currentGit } from './evidence-provenance.mjs';

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
  {
    name: 'home',
    path: '/',
    must: ['LIFEOS / HOME', 'Ask with context', 'Review queue', 'Life spaces', 'Recent updates', 'Open Sources'],
    forbidden: ['Record not found'],
  },
  {
    name: 'food',
    path: '/food',
    must: ['Meal timeline', 'Review queue', 'Pantry-aware suggestion', 'Today', 'Kitchen', 'Plan', 'Recipes', 'Shop', 'Load demo', 'Pantry', 'Meal', 'Archive', 'Undo', 'Export', 'Restore'],
    forbidden: ['Record not found'],
  },
  {
    name: 'search',
    path: '/search',
    must: ['LIFEOS / SEARCH', 'Search food.', 'Quick actions', 'Ask Food AI', 'Add food'],
  },
  {
    name: 'capture',
    path: '/capture',
    must: ['LIFEOS / ADD', 'Add food.', 'INBOX FIRST', 'Food', 'Save capture'],
    forbidden: ['this phase', 'inbox (preview)', 'session fallback'],
  },
  {
    name: 'sources',
    path: '/sources',
    must: ['LIFEOS / SOURCES', 'Your food data homes.', 'LOCAL FIRST', 'Pull what you want. Keep control.', 'What Food Chat can cite'],
  },
  {
    name: 'chat',
    path: '/chat',
    must: ['LIFEOS / CHAT', 'Ask, compare, plan, then act.', 'Sources in context', 'Assistant route', 'Undo', 'Sources'],
  },
  {
    name: 'config',
    path: '/config',
    must: ['Active package contract', 'Screen Builder', 'Collections', 'MCP contract', 'Visual identity editor', 'Operating view order'],
  },
  {
    name: 'settings',
    path: '/settings',
    must: ['LIFEOS / CONNECTIONS', 'Choose AI and data sources.', 'Local answers first', 'No external sources', 'APP BEHAVIOR'],
  },
];

const { chromium } = requirePlaywright();
const executablePath = chromeExecutable();
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({
  headless: true,
  ...(executablePath ? { executablePath } : {}),
});

const results = [];
const viewports = [
  { label: 'desktop', width: 1500, height: 1100 },
  { label: 'mobile', width: 390, height: 900 },
];

for (const viewport of viewports) {
  const page = await browser.newPage({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 });
  for (const route of routes) {
    process.stdout.write(`[web-product] ${route.name}-${viewport.label}\n`);
    const result = {
      name: `${route.name}-${viewport.label}`,
      route: route.name,
      viewport,
      url: `${baseUrl}${route.path}`,
      ok: false,
      missing: [],
      screenshot: join(outDir, `${route.name}-${viewport.label}.png`),
      consoleErrors: [],
      horizontalOverflow: null,
    };
    const consoleErrors = [];
    page.removeAllListeners('console');
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text());
    });
    try {
      await page.goto(`${baseUrl}/_sitemap`, { waitUntil: 'domcontentloaded', timeout: 20000 });
      await page.evaluate((settings) => {
        localStorage.removeItem('lifeos.settings.v1');
        if (settings) localStorage.setItem('lifeos.settings.v1', JSON.stringify(settings));
      }, route.localSettings ?? null);
      await page.goto(result.url, { waitUntil: 'networkidle', timeout: 20000 });
      await page.waitForFunction(
        (needles) => needles.every((needle) => document.body?.innerText?.includes(needle)),
        route.must,
        { timeout: 12000 },
      ).catch(() => undefined);
      const text = await page.locator('body').innerText({ timeout: 8000 });
      result.missing = route.must.filter((needle) => !text.includes(needle));
      if (route.forbidden) {
        for (const needle of route.forbidden) {
          if (text.includes(needle)) result.missing.push(`forbidden:${needle}`);
        }
      }
      result.consoleErrors = consoleErrors;
      result.horizontalOverflow = await page.evaluate(() => {
        const doc = document.scrollingElement || document.documentElement;
        return Math.max(0, doc.scrollWidth - window.innerWidth);
      });
      await route.inspect?.(page);
      await page.screenshot({ path: result.screenshot, fullPage: true });
      result.ok = result.missing.length === 0 && consoleErrors.length === 0 && result.horizontalOverflow <= 2;
    } catch (error) {
      result.error = error instanceof Error ? error.message : String(error);
    }
    results.push(result);
    process.stdout.write(`[web-product] ${result.ok ? 'ok' : 'fail'} ${result.name}\n`);
    if (!result.ok) {
      process.stdout.write(`${JSON.stringify({ missing: result.missing, error: result.error, consoleErrors: result.consoleErrors, horizontalOverflow: result.horizontalOverflow })}\n`);
    }
  }
  await page.close();
}

await browser.close();

const evidence = {
  proof: 'lifeos_web_product_smoke',
  scope: 'food_debug_app_only',
  pass: results.every((result) => result.ok),
  baseUrl,
  checked_at: new Date().toISOString(),
  git: currentGit(root),
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
