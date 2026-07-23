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
    must: ['LIFEOS / HOME', 'Green dal + rice', 'Open today', 'Ask with context', 'Review queue', 'Life spaces', 'Recent updates', 'Open Sources'],
    forbidden: ['Record not found', '0 food records'],
  },
  {
    name: 'food',
    path: '/food',
    must: ['LIFEOS / FOOD', 'Tonight: Green dal + rice', '15 food records', 'Nutrition lens', 'Shopping gaps', 'Lots and movements', 'Spend and receipt health', 'Yogurt lot · open tub', 'Food operating board', 'TONIGHT OPERATING TABLE', 'Cook decision, pantry state, shopping gap, next move', 'Meals', 'Kitchen', 'Shopping', 'Review before anything writes', 'Food can be customized'],
    forbidden: ['0 food records', 'Record not found'],
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
    must: ['Chef board', 'Custom app-edited block.'],
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
    must: ['LIFEOS / HEALTH', 'Run your health workspace.', 'Health sources', 'Overview', 'Journal', 'Plans'],
  },
  {
    name: 'record-green-dal',
    path: '/record/meal-green-dal',
    must: [
      'FOOD INTELLIGENCE',
      'Nutrition, availability, shopping and memory in one page.',
      'NEED / BUY',
      'MEMORY',
      'Nutrition profile',
      'SERVING CONTROLS',
      'Macro lens',
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
    forbidden: ['Record not found'],
  },
  {
    name: 'record-configured-order',
    path: '/record/meal-green-dal',
    localSettings: {
      runtime: {
        surfaceConfig: {
          record: {
            mainSectionOrder: 'history,nutrition,ingredients,instructions,editableNote',
            sideSectionOrder: 'provenance,properties,relations',
          },
        },
      },
    },
    must: ['Cooking log and variations', 'Nutrition profile', 'Provenance', 'Properties'],
    orderedBefore: ['Cooking log and variations', 'Nutrition profile'],
    alsoOrderedBefore: ['Provenance', 'Properties'],
  },
  {
    name: 'chat',
    path: '/chat',
    must: ['LIFEOS / CHAT', 'Ask, compare, plan, then act.', 'Sources in context', 'Assistant route', 'Undo', 'THREADS', 'ANSWER TABLE', 'Source evidence', 'Copy', 'Save', 'Follow-up', 'Sources', 'Regenerate'],
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
    must: ['LIFEOS / SOURCES', 'Your food data homes.', 'LOCAL FIRST', 'Pull what you want. Keep control.', 'Manual pulls keep the graph understandable and portable.', 'data homes', 'What Food Chat can cite'],
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
    must: ['LIFEOS / SOURCES', 'What Food Chat can cite', '2 citeable homes'],
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
    must: ['LIFEOS / SOURCES', 'What Food Chat can cite', 'Your food data homes.'],
    orderedBefore: ['What Food Chat can cite', 'Your food data homes.'],
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
    must: ['LIFEOS / SOURCES', 'Your health data homes.', 'Pull what you want. Keep control.', 'What Health Chat can cite'],
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
      'operating views',
      'Operating view order',
      'Prompt presets',
      'Composer context note',
      'Search',
      'Capture',
      'Result cards',
      'Destination hint',
      'Health Connect',
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
    must: ['LIFEOS / CONNECTIONS', 'Choose AI and data sources.', 'Local answers first', 'No external sources', 'APP BEHAVIOR', 'Direct tokens stay on this device', 'Health Connect'],
  },
  {
    name: 'health-diagnostics',
    path: '/health-diagnostics',
    must: ['LIFEOS / HEALTH CONNECT', 'Health data access', 'You stay in control of permissions.', 'WHAT THIS MEANS', 'AVAILABLE TO LIFEOS', 'PRIVACY', 'LAST CHECKED', 'NEXT STEP'],
    forbidden: ['round-trip proof', 'native evidence', 'clientRecordId', 'insertedIds', 'readBeforeDelete', 'readAfterDelete', 'Temporary test ID', 'Rows written', 'Read before cleanup', 'Read after cleanup', 'technical receipt'],
  },
  {
    name: 'health-diagnostics-configured',
    path: '/health-diagnostics',
    localSettings: {
      runtime: {
        surfaceConfig: {
          health: {
            sectionOrder: 'details,status',
            showHero: false,
            showStatusCard: true,
            showTechnicalReceipt: false,
            showDetails: true,
          },
        },
      },
    },
    must: ['AVAILABLE TO LIFEOS', 'PRIVACY', 'LOCAL DEVICE CHECK'],
    forbidden: ['LIFEOS / HEALTH CONNECT', 'HC_ROUNDTRIP'],
  },
  {
    name: 'search',
    path: '/search',
    must: ['LIFEOS / SEARCH', 'Search food.', 'Quick actions', 'Ask Food AI', 'Add food'],
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
    must: ['LIFEOS / SEARCH', 'Search health.', 'Ask Health AI', 'Add health'],
  },
  {
    name: 'capture',
    path: '/capture',
    must: ['LIFEOS / ADD', 'Add food.', 'INBOX FIRST', 'Food', 'Save capture'],
    forbidden: ['this phase', 'inbox (preview)', 'session fallback'],
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
    must: ['LIFEOS / ADD', 'Add health.', 'Health', 'Save capture'],
    forbidden: ['this phase', 'inbox (preview)', 'session fallback'],
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
      if (route.orderedBefore) {
        const [first, second] = route.orderedBefore;
        if (text.indexOf(first) < 0 || text.indexOf(second) < 0 || text.indexOf(first) > text.indexOf(second)) {
          result.missing.push(`${first} before ${second}`);
        }
      }
      if (route.alsoOrderedBefore) {
        const [first, second] = route.alsoOrderedBefore;
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
