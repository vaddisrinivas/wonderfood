#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { basename, dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = execFileSync('git', ['rev-parse', '--show-toplevel'], {
  cwd: scriptDir,
  encoding: 'utf8',
}).trim();
const auditPath = resolve(repoRoot, 'docs/REPOSITORY-AUDIT.md');
const auditRel = 'docs/REPOSITORY-AUDIT.md';

const excludedSegments = new Set(['node_modules', 'dist', 'build', '.gradle', '.cxx']);
const binaryExtensions = new Set([
  '.png',
  '.jpg',
  '.jpeg',
  '.gif',
  '.webp',
  '.mp4',
  '.mov',
  '.ttf',
  '.otf',
  '.woff',
  '.woff2',
  '.jar',
  '.ico',
  '.pdf',
  '.apk',
  '.aab',
]);

const tracked = execFileSync('git', ['ls-files', '-z'], { cwd: repoRoot, encoding: 'buffer' })
  .toString('utf8')
  .split('\0')
  .filter(Boolean)
  .filter((path) => path !== auditRel)
  .filter((path) => !isExcluded(path));

tracked.sort((left, right) => left.localeCompare(right));

const entries = [...tracked.map((path) => describePath(path)), describeSelfPath()];
entries.sort((left, right) => left.path.localeCompare(right.path));

const trackedCount = tracked.length;
const textCount = entries.filter((entry) => entry.review === 'verified').length;
const heuristicCount = entries.length - textCount;

let selfSizeLabel = 'pending';
let rendered = '';

for (let attempt = 0; attempt < 6; attempt += 1) {
  rendered = renderAudit({
    repoRoot,
    auditPath,
    auditRel,
    baseCommit: execFileSync('git', ['rev-parse', '--short', 'HEAD'], {
      cwd: repoRoot,
      encoding: 'utf8',
    }).trim(),
    trackedCount,
    totalCount: entries.length,
    textCount,
    heuristicCount,
    selfSizeLabel,
    entries,
  });

  mkdirSync(dirname(auditPath), { recursive: true });
  writeFileSync(auditPath, rendered, 'utf8');

  const nextSelfSizeLabel = formatSize(statSync(auditPath).size);
  if (nextSelfSizeLabel === selfSizeLabel) {
    break;
  }
  selfSizeLabel = nextSelfSizeLabel;
}

rendered = readFileSync(auditPath, 'utf8');
const tableRows = rendered
  .split('\n')
  .filter((line) => line.startsWith('| ['))
  .length;

if (tableRows !== entries.length) {
  throw new Error(`Row count mismatch: expected ${entries.length}, found ${tableRows}`);
}

for (const entry of entries) {
  const abs = resolve(repoRoot, entry.path);
  if (!existsSync(abs)) {
    throw new Error(`Missing linked file: ${entry.path}`);
  }
}

console.log(`Repository audit written: ${entries.length} rows (${trackedCount} tracked + self), ${textCount} verified, ${heuristicCount} heuristic.`);

function renderAudit({ repoRoot, auditPath, auditRel, baseCommit, trackedCount, totalCount, textCount, heuristicCount, selfSizeLabel, entries }) {
  const lines = [];
  lines.push('# Repository Audit');
  lines.push('');
  lines.push(`- Snapshot: \`${baseCommit}\``);
  lines.push(`- Inventory: ${trackedCount} tracked files + 1 catalog = ${totalCount} rows`);
  lines.push(`- Review status: ${textCount} verified text/source rows, ${heuristicCount} heuristic binary rows`);
  lines.push('- Legend: `review=verified` means the generator read the file fully; `review=heuristic` means metadata-only for binary rows.');
  lines.push('- Lineage: `source`, `generated`, `binary`, or `historical`.');
  lines.push('');
  lines.push('| file | type | role | review | static risks | size | lineage |');
  lines.push('| --- | --- | --- | --- | --- | --- | --- |');

  for (const entry of entries) {
    const sizeLabel = entry.path === auditRel ? selfSizeLabel : entry.sizeLabel;
    lines.push(`| ${[
      linkFor(entry.path, repoRoot),
      escapeCell(entry.type),
      escapeCell(entry.role),
      escapeCell(entry.review),
      escapeCell(entry.risks),
      escapeCell(sizeLabel),
      escapeCell(entry.lineage),
    ].join(' | ')} |`);
  }

  lines.push('');
  lines.push('Verified rows come from full text reads; binary rows are classified from path and file metadata. Regenerate this catalog with `node scripts/quality/generate-repository-audit.mjs`.');
  lines.push('');
  return lines.join('\n');
}

function describePath(path) {
  const abs = resolve(repoRoot, path);
  const binary = isBinaryPath(path);
  const stat = existsSync(abs) ? statSync(abs) : null;
  const sizeLabel = stat ? formatSize(stat.size) : 'pending';
  const text = !binary ? readFileSync(abs, 'utf8') : null;

  return {
    path,
    sizeLabel,
    review: binary ? 'heuristic' : 'verified',
    lineage: classifyLineage(path, binary),
    type: classifyType(path, binary),
    role: classifyRole(path, binary),
    risks: classifyRisks(path, binary),
    text,
  };
}

function describeSelfPath() {
  return {
    path: auditRel,
    sizeLabel: 'pending',
    review: 'verified',
    lineage: 'generated',
    type: 'doc',
    role: 'repository audit',
    risks: 'regen drift; source mismatch',
    text: null,
  };
}

function classifyType(path, binary) {
  if (path === 'docs/REPOSITORY-AUDIT.md') {
    return 'doc';
  }

  if (path === 'package-lock.json' || path.endsWith('/package-lock.json')) {
    return 'lockfile';
  }

  if (binary) {
    return 'asset';
  }

  if (isConfigPath(path)) {
    return 'config';
  }

  if (path.startsWith('tests/') || path.startsWith('server/test/')) {
    return 'test';
  }

  if (path.startsWith('scripts/')) {
    return 'script';
  }

  if (path.startsWith('docs/')) {
    return 'doc';
  }

  if (path.startsWith('android/') || path.endsWith('.kt') || path.endsWith('.java') || path.endsWith('.xml') || path.endsWith('.gradle') || path.endsWith('.properties') || path.endsWith('.pro')) {
    return 'native';
  }

  if (path.startsWith('packages/domain-config/')) {
    if (path.includes('/schemas/') || path.endsWith('.schema.json')) {
      return 'schema';
    }
    return 'data';
  }

  if (path.startsWith('app/') || path.startsWith('src/') || path.startsWith('server/src/') || path.startsWith('spikes/')) {
    return 'code';
  }

  if (path.startsWith('assets/')) {
    return 'asset';
  }

  if (path.endsWith('.json') || path.endsWith('.csv') || path.endsWith('.txt')) {
    return 'data';
  }

  if (path.endsWith('.ts') || path.endsWith('.tsx') || path.endsWith('.js') || path.endsWith('.mjs') || path.endsWith('.py') || path.endsWith('.sh')) {
    return 'code';
  }

  return 'data';
}

function classifyRole(path, binary) {
  if (path === 'docs/REPOSITORY-AUDIT.md') {
    return 'repository audit';
  }

  if (binary) {
    return 'binary asset';
  }

  if (isConfigPath(path)) {
    return 'workspace config';
  }

  if (path.startsWith('src/domain/')) return 'domain model';
  if (path.startsWith('src/db/')) return 'storage layer';
  if (path.startsWith('src/ops/')) return 'operation engine';
  if (path.startsWith('src/providers/')) return 'sync provider';
  if (path.startsWith('src/actions/')) return 'action engine';
  if (path.startsWith('src/chat/')) return 'chat runtime';
  if (path.startsWith('src/config/')) return 'runtime config';
  if (path.startsWith('src/components/')) return 'shared UI';
  if (path.startsWith('src/health/')) return 'health check';
  if (path.startsWith('app/(tabs)/')) return 'tab screen';
  if (path.startsWith('app/')) return 'app route';
  if (path.startsWith('server/src/kernel/')) return 'backend kernel';
  if (path.startsWith('server/src/providers/')) return 'provider adapter';
  if (path.startsWith('server/src/workflows/')) return 'workflow engine';
  if (path.startsWith('server/src/mcp/')) return 'MCP transport';
  if (path.startsWith('server/src/agents/')) return 'agent runtime';
  if (path.startsWith('server/src/')) return 'backend service';
  if (path.startsWith('server/test/')) return 'server contract test';
  if (path.startsWith('tests/')) return 'test coverage';
  if (path.startsWith('scripts/quality/')) return 'quality gate';
  if (path.startsWith('scripts/')) return 'utility script';
  if (path.startsWith('packages/domain-config/domains/')) return 'domain manifest';
  if (path.startsWith('packages/domain-config/schemas/')) return 'schema definition';
  if (path.startsWith('packages/domain-config/templates/generated/')) return 'generated template data';
  if (path.startsWith('packages/domain-config/templates/')) return 'template source';
  if (path.startsWith('packages/domain-config/workflows/')) return 'workflow config';
  if (path.startsWith('packages/domain-config/agents/')) return 'agent registry';
  if (path.startsWith('packages/shared/contracts/')) return 'shared contract';
  if (path.startsWith('docs/lifeos/')) return 'LifeOS doc';
  if (path.startsWith('docs/ai/')) return 'AI contract doc';
  if (path.startsWith('docs/quality/')) return 'quality evidence';
  if (path.startsWith('docs/testing/')) return 'testing policy';
  if (path.startsWith('docs/marketing/')) return 'marketing doc';
  if (path.startsWith('docs/distribution/')) return 'distribution doc';
  if (path.startsWith('docs/release/')) return 'release doc';
  if (path.startsWith('docs/adr/')) return 'architecture doc';
  if (path.startsWith('docs/')) return 'documentation';
  if (path.startsWith('spikes/')) return 'experimental spike';
  if (path.startsWith('fastlane/metadata/')) return 'store listing metadata';
  if (path.startsWith('android/')) return 'Android native';
  if (path.startsWith('assets/')) return 'app asset';
  return 'repo artifact';
}

function classifyRisks(path, binary) {
  if (binary) {
    return 'size bloat; no semantic diff';
  }

  if (path === 'package-lock.json' || path.endsWith('/package-lock.json')) {
    return 'dependency drift; reproducibility loss';
  }

  if (isConfigPath(path)) {
    return 'build drift; env mismatch';
  }

  if (path.startsWith('tests/') || path.startsWith('server/test/')) {
    return 'fixture drift; false confidence';
  }

  if (path.startsWith('docs/')) {
    return path.includes('/report') || path.includes('/convergence/') || path.includes('/ledger')
      ? 'evidence rot; false confidence'
      : 'stale guidance; scope drift';
  }

  if (path.startsWith('scripts/')) {
    return 'workflow drift; shell breakage';
  }

  if (path.startsWith('android/')) {
    return 'platform drift; build breakage';
  }

  if (path.startsWith('packages/domain-config/')) {
    return 'schema drift; contract mismatch';
  }

  if (path.startsWith('src/') || path.startsWith('app/') || path.startsWith('server/src/') || path.startsWith('spikes/')) {
    return 'behavior drift; boundary mismatch';
  }

  if (path.startsWith('fastlane/metadata/')) {
    return 'store copy drift; release mismatch';
  }

  if (path.startsWith('assets/')) {
    return 'brand drift; size bloat';
  }

  return 'drift risk; manual review needed';
}

function classifyLineage(path, binary) {
  if (path === 'package-lock.json' || path.endsWith('/package-lock.json') || path.includes('/generated/')) {
    return 'generated';
  }

  if (binary) {
    return 'binary';
  }

  const historicalMarkers = ['report', 'ledger', 'convergence', 'baseline', 'rollout', 'proof', 'evidence', 'changelog', 'REVIEW', 'RELEASE_CHECKLIST'];
  if (historicalMarkers.some((marker) => path.includes(marker))) {
    return 'historical';
  }

  return 'source';
}

function isBinaryPath(path) {
  const ext = extname(path).toLowerCase();
  if (binaryExtensions.has(ext)) {
    return true;
  }

  const base = basename(path).toLowerCase();
  if (base.endsWith('.ttf') || base.endsWith('.otf') || base.endsWith('.png') || base.endsWith('.jpg') || base.endsWith('.jpeg') || base.endsWith('.webp') || base.endsWith('.gif') || base.endsWith('.mp4') || base.endsWith('.jar')) {
    return true;
  }

  if (path.includes('/.git/')) {
    return true;
  }

  return false;
}

function isConfigPath(path) {
  if (path === '.env.example' || path === 'package.json' || path === 'server/package.json' || path === 'tsconfig.json' || path === 'vitest.config.ts' || path === 'metro.config.js' || path === 'app.json' || path === 'eas.json' || path === '.gitleaks.toml') {
    return true;
  }

  if (path.startsWith('.github/')) {
    return true;
  }

  if (path.startsWith('.maestro/')) {
    return true;
  }

  if (path.startsWith('android/') && (path.endsWith('.gradle') || path.endsWith('.properties') || path.endsWith('.bat') || path.endsWith('.sh'))) {
    return true;
  }

  return false;
}

function isExcluded(path) {
  const segments = path.split('/');
  return segments.some((segment) => excludedSegments.has(segment));
}

function linkFor(path, root) {
  return `[${path}](${resolve(root, path)})`;
}

function escapeCell(value) {
  return String(value).replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
}

function formatSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }

  const units = ['KB', 'MB', 'GB'];
  let size = bytes / 1024;
  let unitIndex = 0;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(1)} ${units[unitIndex]}`;
}
