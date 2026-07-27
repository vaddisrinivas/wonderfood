import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  collectAppPackageValidationCategories,
  collectAppPackageValidationIssues,
  type PackageValidationCategory,
} from '@/packages/shared/contracts/package';
import { activateAppPackage } from '@/src/db/app-package-registry';
import { MemoryDb } from '@/tests/helpers/memory-db';

type FixtureCase = {
  path: string;
  valid: boolean;
  errorCategory?: PackageValidationCategory;
};

const fixtureDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../fixtures/package-validation');
const manifest = JSON.parse(readFileSync(path.join(fixtureDir, 'manifest.json'), 'utf8')) as FixtureCase[];

function readFixture(caseFile: string): unknown {
  return JSON.parse(readFileSync(path.join(fixtureDir, caseFile), 'utf8'));
}

describe('package validation parity fixtures', () => {
  for (const fixture of manifest) {
    it(fixture.path, async () => {
      const pkg = readFixture(fixture.path);
      const issues = collectAppPackageValidationIssues(pkg);
      const categories = collectAppPackageValidationCategories(pkg);

      if (fixture.valid) {
        expect(categories).toEqual([]);
        await expect(activateAppPackage(new MemoryDb() as any, pkg as any)).resolves.toMatchObject({
          id: (pkg as any).id,
          version: (pkg as any).version,
        });
        return;
      }

      expect(categories).toContain(fixture.errorCategory);
      const matchingIssue = issues.find((issue) => issue.category === fixture.errorCategory);
      expect(matchingIssue).toBeDefined();
      await expect(activateAppPackage(new MemoryDb() as any, pkg as any)).rejects.toThrow(matchingIssue!.message);
    });
  }
});
