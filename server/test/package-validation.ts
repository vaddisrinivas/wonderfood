import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

import {
  collectAppPackageValidationCategories,
  collectAppPackageValidationIssues,
  type PackageValidationCategory,
} from '@/packages/shared/contracts/package';
import { validateAppPackage } from '../src/kernel/package';

type FixtureCase = {
  path: string;
  valid: boolean;
  errorCategory?: PackageValidationCategory;
};

const fixtureDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'fixtures/package-validation');
const manifest = JSON.parse(readFileSync(path.join(fixtureDir, 'manifest.json'), 'utf8')) as FixtureCase[];

function readFixture(caseFile: string): unknown {
  return JSON.parse(readFileSync(path.join(fixtureDir, caseFile), 'utf8'));
}

for (const fixture of manifest) {
  const pkg = readFixture(fixture.path);
  const issues = collectAppPackageValidationIssues(pkg);
  const categories = collectAppPackageValidationCategories(pkg);
  const result = validateAppPackage(pkg);

  assert.equal(result.valid, fixture.valid, `${fixture.path} valid mismatch`);
  if (fixture.valid) {
    assert.deepEqual(categories, [], `${fixture.path} should have no shared issues`);
    continue;
  }

  assert.ok(fixture.errorCategory, `${fixture.path} missing expected category`);
  assert.ok(categories.includes(fixture.errorCategory), `${fixture.path} missing category ${fixture.errorCategory}`);
  const matchingIssue = issues.find((issue) => issue.category === fixture.errorCategory);
  assert.ok(matchingIssue, `${fixture.path} missing issue for ${fixture.errorCategory}`);
  assert.equal(result.valid, false);
  assert.ok(result.errors.includes(matchingIssue.message), `${fixture.path} missing server error ${matchingIssue.message}`);
}

console.log(`package-validation fixtures passed: ${manifest.length}`);
