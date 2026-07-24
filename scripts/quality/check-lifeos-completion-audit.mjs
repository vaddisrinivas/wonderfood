#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { currentGit, readEvidence, validateEvidenceEnvelope, validateSha256Artifact } from './evidence-provenance.mjs';

const root = process.cwd();
const evidenceDir = join(root, 'app', 'build', 'evidence');
const outPath = join(evidenceDir, 'lifeos-completion-audit.json');

function readJson(relativePath) {
  return readEvidence(root, relativePath);
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
const current = currentGit(root);
const providerAuthority = newestProviderAuthorityReceipt();
const providerProvenance = providerAuthority
  ? validateEvidenceEnvelope(root, providerAuthority.relative_path, providerAuthority.receipt, current)
  : { valid: false, issues: ['evidence_missing_or_invalid_json'] };
const workflow = readJson('app/build/evidence/workflow/workflow-resume-cancel-proof.json');
const release = readJson('app/build/evidence/release-readiness.json');
const accessibility = readJson('app/build/evidence/accessibility/accessibility-smoke.json');
const webProduct = readJson('app/build/evidence/web-product-smoke/web-product-smoke.json');
const visualMatrix = readJson('app/build/evidence/visual-state-matrix/visual-state-matrix.json');
const nativeVisualMatrix = readJson('app/build/evidence/native-visual-matrix/native-visual-matrix.json');
const responsiveVisualMatrix = readJson('app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json');
const performanceBudget = readJson('app/build/evidence/performance/performance-budget.json');
const productPolish = readJson('app/build/evidence/product-polish/product-polish-review.json');
const androidArtifacts = readJson('app/build/evidence/android-release-artifacts.json');
const iosExport = readJson('app/build/evidence/ios-export.json');

const evidencePaths = {
  workflow: 'app/build/evidence/workflow/workflow-resume-cancel-proof.json',
  release: 'app/build/evidence/release-readiness.json',
  accessibility: 'app/build/evidence/accessibility/accessibility-smoke.json',
  webProduct: 'app/build/evidence/web-product-smoke/web-product-smoke.json',
  visualMatrix: 'app/build/evidence/visual-state-matrix/visual-state-matrix.json',
  nativeVisualMatrix: 'app/build/evidence/native-visual-matrix/native-visual-matrix.json',
  responsiveVisualMatrix: 'app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json',
  performanceBudget: 'app/build/evidence/performance/performance-budget.json',
  productPolish: 'app/build/evidence/product-polish/product-polish-review.json',
  androidArtifacts: 'app/build/evidence/android-release-artifacts.json',
  iosExport: 'app/build/evidence/ios-export.json',
};
const provenance = Object.fromEntries(Object.entries(evidencePaths).map(([key, path]) => [key, validateEvidenceEnvelope(root, path, readJson(path), current)]));
const androidArtifactContentIssues = androidArtifacts
  ? validateSha256Artifact(root, androidArtifacts.apk).concat(validateSha256Artifact(root, androidArtifacts.aab))
  : ['artifact_missing:android_release_artifacts'];
if (androidArtifactContentIssues.length) {
  provenance.androidArtifacts = {
    ...(provenance.androidArtifacts ?? { valid: false, issues: [] }),
    valid: false,
    issues: [...(provenance.androidArtifacts?.issues ?? []), ...androidArtifactContentIssues],
  };
}
const valid = (key) => provenance[key]?.valid === true;
const provenanceRemaining = (key) => provenance[key]?.issues?.map((issue) => `evidence_provenance:${issue}`) ?? ['evidence_provenance:missing'];

const authorityPassed = providerProvenance.valid && providerAuthority?.receipt?.all_authority_checks_passed === true;
const workflowPassed = valid('workflow') && workflow?.all_passed === true;
const releaseReady = valid('release') && release?.release_ready === true;
const visualSmokePassed = valid('accessibility') && valid('webProduct') && accessibility?.pass === true && webProduct?.pass === true;
const visualMatrixPassed = valid('visualMatrix') && visualMatrix?.status === 'passed';
const nativeVisualMatrixPassed = valid('nativeVisualMatrix') && nativeVisualMatrix?.status === 'passed';
const responsiveVisualMatrixPassed = valid('responsiveVisualMatrix') && responsiveVisualMatrix?.status === 'passed';
const performanceBudgetPassed = valid('performanceBudget') && performanceBudget?.status === 'passed';
const productPolishPassed = valid('productPolish') && productPolish?.status === 'passed';
const androidArtifactsCurrent = valid('androidArtifacts') && provenance.androidArtifacts?.git_head === head;

const items = [
  item(
    'live_user_token_notion_sheets_authority',
    'Live user-token Notion and Sheets authority proof',
    authorityPassed ? 'passed' : 'missing',
    providerAuthority?.relative_path ?? null,
    authorityPassed ? [] : providerProvenance.issues.map((issue) => `evidence_provenance:${issue}`).concat(['Run check:provider-standalone-authority with live Notion and Sheets credentials.']),
  ),
  item(
    'workflow_resume_cancel',
    'Workflow resume/cancel proof',
    workflowPassed ? 'passed' : 'missing',
    'app/build/evidence/workflow/workflow-resume-cancel-proof.json',
    workflowPassed ? [] : (provenanceRemaining('workflow').concat(['Run phase7:check:workflow-resume-cancel and inspect all_passed.'])),
  ),
  item(
    'android_ios_release',
    'Android/iOS release readiness',
    releaseReady ? 'passed' : 'blocked',
    'app/build/evidence/release-readiness.json',
    releaseReady ? [] : (provenanceRemaining('release').concat(release?.blockers ?? ['release_readiness_evidence_missing'])),
  ),
  item(
    'web_visual_state_matrix',
    'Web visual state matrix',
    visualMatrixPassed ? 'passed' : 'missing',
    'app/build/evidence/visual-state-matrix/visual-state-matrix.json',
    visualMatrixPassed ? [] : provenanceRemaining('visualMatrix').concat(['Run phase9:check:visual-state-matrix after check:web-product and check:accessibility-smoke.']),
  ),
  item(
    'native_visual_state_matrix',
    'Native visual state matrix',
    nativeVisualMatrixPassed ? 'passed' : 'missing',
    'app/build/evidence/native-visual-matrix/native-visual-matrix.json',
    nativeVisualMatrixPassed ? [] : provenanceRemaining('nativeVisualMatrix').concat(['Run phase9:check:native-visual-matrix on an Android emulator with the release APK.']),
  ),
  item(
    'responsive_visual_state_matrix',
    'Tablet/foldable responsive visual matrix',
    responsiveVisualMatrixPassed ? 'passed' : 'missing',
    'app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json',
    responsiveVisualMatrixPassed ? [] : provenanceRemaining('responsiveVisualMatrix').concat(['Run phase9:check:responsive-visual-matrix against tablet/foldable viewports.']),
  ),
  item(
    'performance_budget',
    'Performance budget',
    performanceBudgetPassed ? 'passed' : 'missing',
    'app/build/evidence/performance/performance-budget.json',
    performanceBudgetPassed ? [] : provenanceRemaining('performanceBudget').concat(['Run phase9:check:performance-budget after web/android/iOS exports.']),
  ),
  item(
    'final_visual_accessibility_polish',
    'Final visual/accessibility polish',
    productPolishPassed ? 'passed' : (visualSmokePassed && visualMatrixPassed && nativeVisualMatrixPassed && responsiveVisualMatrixPassed && performanceBudgetPassed ? 'partial' : 'missing'),
    {
      web_product: 'app/build/evidence/web-product-smoke/web-product-smoke.json',
      accessibility: 'app/build/evidence/accessibility/accessibility-smoke.json',
      visual_matrix: 'app/build/evidence/visual-state-matrix/visual-state-matrix.json',
      native_visual_matrix: 'app/build/evidence/native-visual-matrix/native-visual-matrix.json',
      responsive_visual_matrix: 'app/build/evidence/responsive-visual-matrix/responsive-visual-matrix.json',
      performance_budget: 'app/build/evidence/performance/performance-budget.json',
      product_polish_review: 'app/build/evidence/product-polish/product-polish-review.json',
    },
    productPolishPassed
      ? []
      : productPolish?.issues?.length
        ? productPolish.issues
        : visualSmokePassed
      ? [
        'Run phase9:check:product-polish-review and clear its issues.',
      ]
      : ['Run/fix check:web-product and check:accessibility-smoke.'],
  ),
  item(
    'android_release_artifacts_current',
    'Android release artifacts match current HEAD',
    androidArtifactsCurrent ? 'passed' : 'blocked',
    'app/build/evidence/android-release-artifacts.json',
    androidArtifactsCurrent ? [] : provenanceRemaining('androidArtifacts').concat(['Run phase8:check:android-release-artifacts after the latest commit.']),
  ),
  item(
    'ios_export_present',
    'iOS export evidence exists',
    valid('iosExport') && iosExport?.status === 'passed' ? 'passed' : 'missing',
    'app/build/evidence/ios-export.json',
    valid('iosExport') && iosExport?.status === 'passed' ? [] : provenanceRemaining('iosExport').concat(['Run check:ios-export.']),
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
  evidence_provenance: provenance,
};
payload.evidence_provenance.providerAuthority = providerProvenance;

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(outPath, JSON.stringify(payload, null, 2));

const blockers = [...blocked, ...partial, ...missing].map((entry) => `${entry.id}:${entry.status}`);
console.log(`LifeOS completion audit: ${complete ? 'COMPLETE' : 'NOT_COMPLETE'} (${blockers.join(', ') || 'none'}; evidence: ${outPath})`);

if (process.env.REQUIRE_LIFEOS_COMPLETE === '1' && !complete) {
  process.exit(1);
}
