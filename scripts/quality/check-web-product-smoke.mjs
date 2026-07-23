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
  {
    name: 'home',
    path: '/',
    must: ['LIFEOS / HOME', 'Review queue', 'Life spaces', 'Recent graph', 'Source trust', 'Control lives in Settings'],
  },
  {
    name: 'food',
    path: '/food',
    must: ['Package dashboard', 'Tonight decision', 'Nutrition lens', 'Shopping gaps', 'Profile widgets', 'Food sources', 'Food workspace', 'Meals', 'Kitchen', 'Shopping', 'Review before writing', 'Food is the active package'],
  },
  {
    name: 'food-configured-dashboard',
    path: '/food',
    localSettings: {
      runtime: {
        surfaceConfig: {
          food: {
            dashboardBlocks: 'food:chef|Chef board|Custom app-edited block.|action|plum|recipe|chicken|1|/chat',
          },
        },
      },
    },
    must: ['Package dashboard', 'Chef board', 'Custom app-edited block.', 'food.overview · food:chef'],
  },
  {
    name: 'health-active-workspace',
    path: '/food',
    localSettings: {
      runtime: {
        activeDomain: 'health',
        enabledDomains: ['food', 'health'],
      },
    },
    must: ['LIFEOS / HEALTH', 'Run your health workspace from one graph.', 'Today health signal', 'Health workspace', 'Health is the active package'],
  },
  {
    name: 'record-green-dal',
    path: '/record/meal-green-dal',
    must: [
      'Nutrition profile',
      'Ingredients and availability',
      'Available',
      'Shopping',
      'Previous / substitute',
      'Instructions',
      'Cooking log and variations',
      'Previous notes',
      'Variations',
      'Connected records',
      'Provenance',
    ],
  },
  {
    name: 'chat',
    path: '/chat',
    must: ['Talk to your life', 'Chat cites sources', 'Sources', 'Source evidence', 'Undo', 'Open source'],
  },
  {
    name: 'chat-configured-prompts',
    path: '/chat',
    localSettings: {
      runtime: {
        surfaceConfig: {
          chat: {
            promptPresets: 'Plan a protein dinner from pantry\nCompare recipe nutrition with sources',
            contextNote: 'Custom chat context note from app settings.',
          },
        },
      },
    },
    must: ['Plan a protein dinner from pantry', 'Compare recipe nutrition with sources', 'Custom chat context note from app settings.'],
  },
  {
    name: 'sources',
    path: '/sources',
    must: ['LIFEOS / SOURCES', 'Food data stays legible', 'DIRECT SYNC READY', 'Data homes & surfaces', 'sqlite', 'Food local replica', 'What Food Chat can cite'],
  },
  {
    name: 'sources-configured-citations',
    path: '/sources',
    localSettings: {
      runtime: {
        surfaceConfig: {
          sources: {
            citationLimit: '2',
          },
        },
      },
    },
    must: ['LIFEOS / SOURCES', 'What Food Chat can cite', '2 source packs'],
  },
  {
    name: 'sources-configured-order',
    path: '/sources',
    localSettings: {
      runtime: {
        surfaceConfig: {
          sources: {
            sectionOrder: 'citations,hero,metrics,dataHomes,syncPlan,policy,configLink',
          },
        },
      },
    },
    must: ['LIFEOS / SOURCES', 'What Food Chat can cite', 'Food data stays legible'],
    orderedBefore: ['What Food Chat can cite', 'Food data stays legible'],
  },
  {
    name: 'sources-health-active',
    path: '/sources',
    localSettings: {
      runtime: {
        activeDomain: 'health',
        enabledDomains: ['food', 'health'],
      },
    },
    must: ['LIFEOS / SOURCES', 'Health data stays legible', 'Pull Health sources into one local graph', 'Health local replica', 'What Health Chat can cite'],
  },
  {
    name: 'config',
    path: '/config',
    must: [
      'Active package contract',
      'Screen Builder',
      'NEAR-NOTION APP MODEL',
      'screens editable',
      'Open screen',
      'Collections',
      'MCP contract',
      'Screen composition',
      'Every main screen gets runtime knobs',
      'Sources',
      'Citation cards',
      'Manifest Dashboard Blocks',
      'Prompt presets',
      'Composer context note',
      'Search',
      'Capture',
      'Result cards',
      'Destination hint',
      'Portable profile',
      'Load YAML',
      'Apply profile',
      'Schemas & advanced overrides',
    ],
  },
  {
    name: 'system',
    path: '/system',
    must: [
      'LIFEOS / CONTROL DECK',
      'Your app should feel like Notion plus GPT',
      'Screen model',
      'Configure from the app',
      'Glance-style config',
    ],
  },
  {
    name: 'settings',
    path: '/settings',
    must: ['AI providers', 'Data sources', 'LifeOS behavior', 'Direct tokens stay on this device', 'Health Connect'],
  },
  {
    name: 'search',
    path: '/search',
    must: ['LIFEOS / SEARCH', 'Search Food.', 'Quick actions', 'Ask Food AI', 'Create Food record'],
  },
  {
    name: 'search-health-active',
    path: '/search',
    localSettings: {
      runtime: {
        activeDomain: 'health',
        enabledDomains: ['food', 'health'],
      },
    },
    must: ['LIFEOS / SEARCH', 'Search Health.', 'Health records, commands', 'Ask Health AI', 'Create Health record'],
  },
  {
    name: 'capture',
    path: '/capture',
    must: ['LIFEOS / CAPTURE', 'Capture anything', 'INBOX FIRST', 'Food graph', 'Save capture'],
  },
  {
    name: 'capture-health-active',
    path: '/capture',
    localSettings: {
      runtime: {
        activeDomain: 'health',
        enabledDomains: ['food', 'health'],
      },
    },
    must: ['LIFEOS / CAPTURE', 'Health graph', 'Health records', 'Health local graph', 'Save capture'],
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
    const url = `${baseUrl}${route.path}`;
    const result = {
      name: `${route.name}-${viewport.label}`,
      route: route.name,
      viewport,
      url,
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
      await page.goto(url, { waitUntil: 'networkidle', timeout: 20000 });
      const text = await page.locator('body').innerText({ timeout: 8000 });
      result.missing = route.must.filter((needle) => !text.includes(needle));
      if (route.orderedBefore) {
        const [first, second] = route.orderedBefore;
        if (text.indexOf(first) < 0 || text.indexOf(second) < 0 || text.indexOf(first) > text.indexOf(second)) {
          result.missing.push(`${first} before ${second}`);
        }
      }
      result.consoleErrors = consoleErrors;
      result.horizontalOverflow = await page.evaluate(() => {
        const root = document.scrollingElement || document.documentElement;
        return Math.max(0, root.scrollWidth - window.innerWidth);
      });
      await page.screenshot({ path: result.screenshot, fullPage: true });
      result.ok = result.missing.length === 0 && consoleErrors.length === 0 && result.horizontalOverflow <= 2;
    } catch (error) {
      result.error = error instanceof Error ? error.message : String(error);
    }
    results.push(result);
  }
  await page.close();
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
