import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { readJsonStateFile, writeJsonStateFileAtomic } from '../src/providers/json-state';

const tempDir = mkdtempSync(join(tmpdir(), 'lifeos-provider-persistence-'));

const atomicPath = join(tempDir, 'atomic.json');
writeJsonStateFileAtomic(atomicPath, { ok: true, nested: { value: 1 } });
assert.deepEqual(JSON.parse(readFileSync(atomicPath, 'utf-8')), { ok: true, nested: { value: 1 } });
assert.equal(readdirSync(tempDir).some((name) => name.includes('.tmp-')), false, 'atomic write should not leave temp files behind');

const mcpPath = join(tempDir, 'mcp-runtime.json');
writeFileSync(mcpPath, '{"broken":', 'utf-8');
assert.throws(
  () => readJsonStateFile(mcpPath, {
    label: 'MCP runtime state',
    validate: (value): value is { version: 1 } => typeof value === 'object' && value !== null && (value as { version?: unknown }).version === 1,
  }),
  /Corrupt MCP runtime state/,
  'MCP corruption error should be explicit',
);
assert.equal(readdirSync(tempDir).some((name) => /^mcp-runtime\.corrupt-/.test(name)), true, 'corrupt MCP state should be quarantined');

const workflowPath = join(tempDir, 'workflow-runs.json');
writeFileSync(workflowPath, '{"runs":', 'utf-8');
assert.throws(
  () => readJsonStateFile(workflowPath, {
    label: 'workflow checkpoint state',
    validate: (value): value is { runs: Record<string, unknown> } => typeof value === 'object' && value !== null && 'runs' in (value as Record<string, unknown>),
  }),
  /Corrupt workflow checkpoint state/,
  'workflow corruption error should be explicit',
);
assert.equal(readdirSync(tempDir).some((name) => /^workflow-runs\.corrupt-/.test(name)), true, 'corrupt workflow state should be quarantined');

console.log('PASS server/test/provider-persistence-contract.ts');
