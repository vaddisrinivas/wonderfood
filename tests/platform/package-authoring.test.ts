import { fileURLToPath } from 'node:url';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { readAppPackageSourceFolder } from '@/packages/app-compiler';
import type { PackageAuthoringChange } from '@/packages/shared/contracts/package-authoring';
import {
  approvePackageAuthoringEvaluation,
  computePackageSourceRevision,
  evaluatePackageAuthoringChange,
} from '@/src/domain/package-authoring';

const fixtureDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../fixtures/package-source/reference-app');

function loadSource() {
  return readAppPackageSourceFolder(fixtureDir);
}

describe('package authoring', () => {
  it('evaluates source-file patches through the compiler and returns preview/diff', () => {
    const source = loadSource();
    const change: PackageAuthoringChange = {
      schemaVersion: 'utopia.authoring-change.v1',
      baseSourceRevision: computePackageSourceRevision(source),
      intent: 'Add grocery notes to chores',
      proposedBy: 'ai:wonder',
      changes: [
        {
          op: 'replace',
          path: 'collections/chore.json',
          value: {
            fields: {
              ...source.collections!.chore.fields,
              grocery_note: { type: 'text' },
            },
          },
        },
        {
          op: 'replace',
          path: 'screens/chores.json',
          value: {
            ...source.screens!.chores,
            fields: [...source.screens!.chores.fields, 'grocery_note'],
          },
        },
      ],
    };

    const evaluation = evaluatePackageAuthoringChange(source, change);

    expect(evaluation.valid).toBe(true);
    if (!evaluation.valid) throw new Error(evaluation.errors.map((error) => error.message).join(', '));
    expect(evaluation.requiresApproval).toBe(true);
    expect(evaluation.diff.some((entry: any) => entry.kind === 'added')).toBe(true);
    expect((evaluation.preview as any).diffSummary.added).toBeGreaterThan(0);
    expect(evaluation.package.collections.chore.fields.grocery_note.type).toBe('text');
  });

  it('blocks stale base revisions and executable/SQL edits before compilation', () => {
    const source = loadSource();
    const stale = evaluatePackageAuthoringChange(source, {
      schemaVersion: 'utopia.authoring-change.v1',
      baseSourceRevision: 'sha256:deadbeef',
      intent: 'stale change',
      proposedBy: 'ai:wonder',
      changes: [{ op: 'add', path: 'screens/stale.json', value: {} }],
    });
    expect(stale.valid).toBe(false);
    if (stale.valid) throw new Error('expected stale proposal to fail');
    expect(stale.errors.some((error) => error.path === '/baseSourceRevision')).toBe(true);

    const executable = evaluatePackageAuthoringChange(source, {
      schemaVersion: 'utopia.authoring-change.v1',
      baseSourceRevision: computePackageSourceRevision(source),
      intent: 'run code',
      proposedBy: 'ai:wonder',
      changes: [{ op: 'add', path: 'screens/hack.tsx', value: 'import fs from "node:fs"' }],
    });
    expect(executable.valid).toBe(false);
    if (executable.valid) throw new Error('expected executable proposal to fail');
    expect(executable.errors.some((error) => error.message.includes('executable code or SQL'))).toBe(true);
  });

  it('requires non-self approval before activation receipt exists', () => {
    const source = loadSource();
    const evaluation = evaluatePackageAuthoringChange(source, {
      schemaVersion: 'utopia.authoring-change.v1',
      baseSourceRevision: computePackageSourceRevision(source),
      intent: 'Add theme token',
      proposedBy: 'ai:wonder',
      changes: [{ op: 'add', path: 'theme/calm.json', value: { accent: '#7c6f57' } }],
    });
    expect(evaluation.valid).toBe(true);
    if (!evaluation.valid) throw new Error(evaluation.errors.map((error) => error.message).join(', '));

    expect(() => approvePackageAuthoringEvaluation(evaluation, { approvedBy: 'ai:wonder' })).toThrow(/self-approved/);
    const receipt = approvePackageAuthoringEvaluation(evaluation, {
      approvedBy: 'user:srinivas',
      approvedAt: '2026-07-28T00:00:00.000Z',
    });

    expect(receipt.activationAllowed).toBe(true);
    expect(receipt.rollbackSourceRevision).toBe(evaluation.baseSourceRevision);
    expect(receipt.packageChecksum).toBe(evaluation.packageChecksum);
  });
});
