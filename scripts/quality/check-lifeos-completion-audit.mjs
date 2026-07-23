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
const visualMatrix = readJson('app/build/evidence/visual-state-matrix/visual-state-matrix.json');
const nativeVisualMatrix = readJson('app/build/evidence/native-visual-matrix/native-visual-matrix.json');
const responsiveVisualMatrix = readJson('app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json');
const androidArtifacts = readJson('app/build/evidence/android-release-artifacts.json');
const iosExport = readJson('app/build/evidence/ios-export.json');

const authorityPassed = providerAuthority?.receipt?.all_authority_checks_passed === true;
const workflowPassed = workflow?.all_passed === true;
const releaseReady = release?.release_ready === true;
const visualSmokePassed = accessibility?.pass === true && webProduct?.pass === true;
const visualMatrixPassed = visualMatrix?.status === 'passed';
const nativeVisualMatrixPassed = nativeVisualMatrix?.status === 'passed';
const responsiveVisualMatrixPassed = responsiveVisualMatrix?.status === 'passed';
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
    'web_visual_state_matrix',
    'Web visual state matrix',
    visualMatrixPassed ? 'passed' : 'missing',
    'app/build/evidence/visual-state-matrix/visual-state-matrix.json',
    visualMatrixPassed ? [] : ['Run phase9:check:visual-state-matrix after check:web-product and check:accessibility-smoke.'],
  ),
  item(
    'native_visual_state_matrix',
    'Native visual state matrix',
    nativeVisualMatrixPassed ? 'passed' : 'missing',
    'app/build/evidence/native-visual-matrix/native-visual-matrix.json',
    nativeVisualMatrixPassed ? [] : ['Run phase9:check:native-visual-matrix on an Android emulator with the release APK.'],
  ),
  item(
    'responsive_visual_state_matrix',
    'Tablet/foldable responsive visual matrix',
    responsiveVisualMatrixPassed ? 'passed' : 'missing',
    'app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json',
    responsiveVisualMatrixPassed ? [] : ['Run phase9:check:responsive-visual-matrix against tablet/foldable viewports.'],
  ),
  item(
    'final_visual_accessibility_polish',
    'Final visual/accessibility polish',
    visualSmokePassed && visualMatrixPassed && nativeVisualMatrixPassed && responsiveVisualMatrixPassed ? 'partial' : 'missing',
    {
      web_product: 'app/build/evidence/web-product-smoke/web-product-smoke.json',
      accessibility: 'app/build/evidence/accessibility/accessibility-smoke.json',
      visual_matrix: 'app/build/evidence/visual-state-matrix/visual-state-matrix.json',
      native_visual_matrix: 'app/build/evidence/native-visual-matrix/native-visual-matrix.json',
      responsive_visual_matrix: 'app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json',
    },
    visualSmokePassed
      ? [
        'Web/native/responsive visual matrices and accessibility smoke pass, but manual product-grade UI review is still not complete.',
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
