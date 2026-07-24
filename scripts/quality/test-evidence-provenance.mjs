#!/usr/bin/env node
import assert from 'node:assert/strict';
import { currentGit, validateEvidenceEnvelope, validateSha256Artifact } from './evidence-provenance.mjs';

const root = process.cwd();
const current = currentGit(root);
assert.ok(current.head, 'test requires a git HEAD');
const envelope = {
  proof: 'fixture', checked_at: new Date().toISOString(),
  git_head: current.head, branch: current.branch,
  tree_hash: current.tree, dirty: current.dirty, dirty_diff_hash: current.dirtyDiffHash,
};
assert.equal(validateEvidenceEnvelope(root, 'fixture.json', {
  ...envelope,
}, current).valid, true);
assert.deepEqual(validateEvidenceEnvelope(root, 'fixture.json', {
  ...envelope, git_head: 'stale',
}, current).issues, [`stale:git_head:stale:expected:${current.head}`]);
assert.ok(validateEvidenceEnvelope(root, 'fixture.json', {
  ...envelope, git_head: undefined,
}, current).issues.includes('missing:git_head'));
assert.ok(validateEvidenceEnvelope(root, 'fixture.json', null, current).issues.includes('evidence_missing_or_invalid_json'));
assert.equal(validateEvidenceEnvelope(root, 'fixture.json', {
  proof: 'fixture', checked_at: new Date().toISOString(),
  git: { head: current.head, branch: current.branch, tree: current.tree, dirty: current.dirty, dirty_diff_hash: current.dirtyDiffHash },
}, current).valid, true);
assert.deepEqual(validateSha256Artifact(root, { path: 'missing.apk', sha256: '0'.repeat(64) }), ['artifact_missing:file:missing.apk']);
console.log('Evidence provenance validation: PASS');
