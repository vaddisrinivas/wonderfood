#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.cwd();
const distDir = join(root, 'dist', 'ios');
const evidenceDir = join(root, 'app', 'build', 'evidence');
const evidencePath = join(evidenceDir, 'ios-export.json');

mkdirSync(evidenceDir, { recursive: true });

const exportRun = spawnSync('npx', ['expo', 'export', '--platform', 'ios', '--output-dir', 'dist/ios'], {
  cwd: root,
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'pipe'],
});

if (exportRun.status !== 0) {
  process.stderr.write(exportRun.stdout);
  process.stderr.write(exportRun.stderr);
  throw new Error(`iOS export failed with status ${exportRun.status}`);
}

const metadataPath = join(distDir, 'metadata.json');
if (!existsSync(metadataPath)) throw new Error(`iOS metadata missing: ${metadataPath}`);

const metadata = JSON.parse(readFileSync(metadataPath, 'utf8'));
const ios = metadata.fileMetadata?.ios;
if (!ios?.bundle) throw new Error('iOS bundle missing from metadata');
const bundlePath = join(distDir, ios.bundle);
if (!existsSync(bundlePath)) throw new Error(`iOS bundle file missing: ${bundlePath}`);
if (!Array.isArray(ios.assets) || ios.assets.length < 10) throw new Error(`Expected iOS assets, got ${ios.assets?.length ?? 0}`);

const appJson = JSON.parse(readFileSync(join(root, 'app.json'), 'utf8'));
const expo = appJson.expo ?? {};
if (!expo.platforms?.includes('ios')) throw new Error('app.json does not enable ios platform');
if (expo.ios?.bundleIdentifier !== 'com.wonderfood.app') throw new Error('iOS bundleIdentifier mismatch');
if (expo.ios?.buildNumber !== '1') throw new Error('iOS buildNumber mismatch');
if (expo.scheme !== 'wonderfood') throw new Error('Deep link scheme mismatch');

const evidence = {
  status: 'passed',
  checked_at: new Date().toISOString(),
  platform: 'ios',
  app_name: expo.name,
  bundle_identifier: expo.ios.bundleIdentifier,
  build_number: expo.ios.buildNumber,
  scheme: expo.scheme,
  bundle: {
    path: `dist/ios/${ios.bundle}`,
    bytes: statSync(bundlePath).size,
  },
  asset_count: ios.assets.length,
};

writeFileSync(evidencePath, JSON.stringify(evidence, null, 2));
console.log(`iOS export check: PASS (${ios.bundle}; ${ios.assets.length} assets; evidence: ${evidencePath})`);
