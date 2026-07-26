#!/usr/bin/env node

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const guard = fileURLToPath(new URL('./require-disposable-lane.mjs', import.meta.url));

function run(lane, extra = {}) {
  return spawnSync(process.execPath, [guard, lane], {
    encoding: 'utf8',
    env: { PATH: process.env.PATH || '', ...extra },
  });
}

assert.equal(run('provider').status, 2);
assert.equal(run('device').status, 2);
assert.equal(run('provider', {
  WONDERFOOD_LIVE_PROVIDER_ACK: 'DISPOSABLE_PROVIDER_ONLY',
  WONDERFOOD_DISPOSABLE_PROVIDER_TARGET: 'production',
}).status, 2);
assert.equal(run('provider', {
  WONDERFOOD_LIVE_PROVIDER_ACK: 'DISPOSABLE_PROVIDER_ONLY',
  WONDERFOOD_DISPOSABLE_PROVIDER_TARGET: 'notion-ci-fixture',
}).status, 0);
assert.equal(run('device', {
  WONDERFOOD_DEVICE_MUTATION_ACK: 'DISPOSABLE_EMULATOR_ONLY',
  ANDROID_SERIAL: 'physical-secret-serial',
}).status, 2);
assert.equal(run('device', {
  WONDERFOOD_DEVICE_MUTATION_ACK: 'DISPOSABLE_EMULATOR_ONLY',
  ANDROID_SERIAL: 'emulator-5554',
}).status, 0);

const secret = 'must-not-appear';
const blocked = run('provider', { NOTION_TOKEN: secret });
assert.equal(`${blocked.stdout}${blocked.stderr}`.includes(secret), false);

const guardedEntrypoints = [
  'check-live-provider-writeback.ts',
  'check-native-visual-matrix.sh',
  'run-android-lifeos-e2e-proof.sh',
  'run-emulatorx-health-connect.sh',
  'run-google-sheets-live-proof.sh',
  'run-google-sheets-scenario-proof.sh',
  'run-local-postgres-live-proof.sh',
  'run-local-postgres-scenario-proof.sh',
  'run-notion-live-proof.sh',
  'run-notion-scenario-proof.sh',
  'run-postgres-live-proof.sh',
  'run-provider-live-proofs.sh',
  'run-provider-standalone-visual-proof.sh',
];
for (const file of guardedEntrypoints) {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8');
  assert.match(source, /require-disposable-lane\.mjs/);
}

console.log('Disposable live-lane guards: PASS');
