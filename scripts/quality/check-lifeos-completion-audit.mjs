#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.cwd();
const evidenceDir = join(root, 'app', 'build', 'evidence');
const outPath = join(evidenceDir, 'lifeos-completion-audit.json');

function readJson(relativePath) {
  const path = join(root, relativePath);
  if (!existsSync(path)) return null;
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    return null;
  }
}

function git(args) {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
  return result.status === 0 ? result.stdout.trim() : 'unknown';
}

function newestProviderAuthorityReceipt() {
  const dir = join(evidenceDir, 'live-workspace');
  if (!existsSync(dir)) return null;
  const candidates = readdirSync(dir)
    .filter((name) => name.startsWith('provider-standalone-authority-'))
    .map((name) => {
      const proofPath = join(dir, name, 'provider-standalone-authority-proof.json');
      const statId = Number((name.match(/(\d+)/g) ?? ['0']).at(-1));
      return { name, statId, proofPath };
    })
    .filter((item) => existsSync(item.proofPath))
    .sort((left, right) => right.statId - left.statId);
  if (!candidates.length) return null;
  const latest = candidates[0];
  try {
    return {
      relative_path: `app/build/evidence/live-workspace/${latest.name}/provider-standalone-authority-proof.json`,
      receipt: JSON.parse(readFileSync(latest.proofPath, 'utf8')),
    };
  } catch {
    return {
      relative_path: `app/build/evidence/live-workspace/${latest.name}/provider-standalone-authority-proof.json`,
      receipt: null,
    };
  }
}

function item(id, label, status, evidence, remaining = []) {
  return { id, label, status, evidence, remaining };
}

const head = git(['rev-parse', '--short', 'HEAD']);
const branch = git(['branch', '--show-current']);
const providerAuthority = newestProviderAuthorityReceipt();
const workflow = readJson('app/build/evidence/workflow/workflow-resume-cancel-proof.json');
const release = readJson('app/build/evidence/release-readiness.json');
const accessibility = readJson('app/build/evidence/accessibility/accessibility-smoke.json');
const webProduct = readJson('app/build/evidence/web-product-smoke/web-product-smoke.json');
const androidArtifacts = readJson('app/build/evidence/android-release-artifacts.json');
const iosExport = readJson('app/build/evidence/ios-export.json');

const authorityPassed = providerAuthority?.receipt?.all_authority_checks_passed === true;
const workflowPassed = workflow?.all_passed === true;
const releaseReady = release?.release_ready === true;
const visualSmokePassed = accessibility?.pass === true && webProduct?.pass === true;
const androidArtifactsCurrent = androidArtifacts?.git_head === head;

const items = [
  item(
    'live_user_token_notion_sheets_authority',
    'Live user-token Notion and Sheets authority proof',
    authorityPassed ? 'passed' : 'missing',
    providerAuthority?.relative_path ?? null,
    authorityPassed ? [] : ['Run check:provider-standalone-authority with live Notion and Sheets credentials.'],
  ),
  item(
    'workflow_resume_cancel',
    'Workflow resume/cancel proof',
    workflowPassed ? 'passed' : 'missing',
    'app/build/evidence/workflow/workflow-resume-cancel-proof.json',
    workflowPassed ? [] : ['Run phase7:check:workflow-resume-cancel and inspect all_passed.'],
  ),
  item(
    'android_ios_release',
    'Android/iOS release readiness',
    releaseReady ? 'passed' : 'blocked',
    'app/build/evidence/release-readiness.json',
    releaseReady ? [] : (release?.blockers ?? ['release_readiness_evidence_missing']),
  ),
  item(
    'final_visual_accessibility_polish',
    'Final visual/accessibility polish',
    visualSmokePassed ? 'partial' : 'missing',
    {
      web_product: 'app/build/evidence/web-product-smoke/web-product-smoke.json',
      accessibility: 'app/build/evidence/accessibility/accessibility-smoke.json',
    },
    visualSmokePassed
      ? [
        'Smoke gates pass, but final visual state matrix, responsive screenshots, native visual proof, and product-grade UI review are still not complete.',
      ]
      : ['Run/fix check:web-product and check:accessibility-smoke.'],
  ),
  item(
    'android_release_artifacts_current',
    'Android release artifacts match current HEAD',
    androidArtifactsCurrent ? 'passed' : 'blocked',
    'app/build/evidence/android-release-artifacts.json',
    androidArtifactsCurrent ? [] : ['Run phase8:check:android-release-artifacts after the latest commit.'],
  ),
  item(
    'ios_export_present',
    'iOS export evidence exists',
    iosExport?.status === 'passed' ? 'passed' : 'missing',
    'app/build/evidence/ios-export.json',
    iosExport?.status === 'passed' ? [] : ['Run check:ios-export.'],
  ),
];

const complete = items.every((entry) => entry.status === 'passed');
const blocked = items.filter((entry) => entry.status === 'blocked');
const partial = items.filter((entry) => entry.status === 'partial');
const missing = items.filter((entry) => entry.status === 'missing');
const payload = {
  proof: 'lifeos_completion_audit',
  checked_at: new Date().toISOString(),
  git: { branch, head },
  complete,
  summary: {
    passed: items.filter((entry) => entry.status === 'passed').length,
    partial: partial.length,
    blocked: blocked.length,
    missing: missing.length,
  },
  items,
  no_secret_values_written: true,
};

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(outPath, JSON.stringify(payload, null, 2));

const blockers = [...blocked, ...partial, ...missing].map((entry) => `${entry.id}:${entry.status}`);
console.log(`LifeOS completion audit: ${complete ? 'COMPLETE' : 'NOT_COMPLETE'} (${blockers.join(', ') || 'none'}; evidence: ${outPath})`);

if (process.env.REQUIRE_LIFEOS_COMPLETE === '1' && !complete) {
  process.exit(1);
}
