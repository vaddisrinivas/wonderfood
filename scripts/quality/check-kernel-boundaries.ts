import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

type BoundaryFixture = {
  boundary: {
    canonicalWriteAuthority: string[];
  };
};

type Violation = {
  file: string;
  line: number;
  table: string;
  text: string;
};

const rootDir = process.cwd();
const sep = '/';
const fixturePath = join(rootDir, 'tests/contracts/w1-kernel-boundary-fixtures.json');
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8')) as BoundaryFixture;
const allowlistedWriters = new Set(
  fixture.boundary?.canonicalWriteAuthority?.map((entry) => normalizePath(entry)) ?? [],
);

const writePattern = /\b(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+("?(?:records|record_relations|operations)"?)/gi;
const writeTargets = ['records', 'record_relations', 'operations'];
const scanRoots = [join(rootDir, 'src'), join(rootDir, 'server/src')];
const excludeDirs = new Set(['node_modules', 'dist', '.expo', '.turbo', 'coverage', '.git']);
const violations: Violation[] = [];
const foundWriteOwners = new Map<string, Set<string>>();

let scanned = 0;

for (const root of scanRoots) {
  if (!exists(root)) continue;
  scan(root);
}

for (const [file, tables] of foundWriteOwners.entries()) {
  if (!allowlistedWriters.has(file)) {
    for (const table of tables) {
      violations.push({
        file,
        line: 1,
        table,
        text: `unlisted canonical write owner (table ${table})`,
      });
    }
  }
}

for (const writer of allowlistedWriters) {
  if (!foundWriteOwners.has(writer)) {
    violations.push({
      file: writer,
      line: 1,
      table: 'records',
      text: 'listed writer does not perform canonical writes in scan',
    });
  }
}

if (violations.length > 0) {
  console.error('Kernel boundary check failed: canonical write authority violations');
  for (const violation of violations) {
    const lineSuffix = violation.line > 1 ? `:${violation.line}` : '';
    console.error(`  - ${violation.file}${lineSuffix} (${violation.table}) ${violation.text}`);
  }
  process.exit(1);
}

console.log(`Kernel boundaries passed: scanned ${scanned} files`);
console.log(`Canonical write owners: ${foundWriteOwners.size}`);
console.log(`Allowlisted canonical writers: ${allowlistedWriters.size}`);

function scan(directory: string) {
  const entries = readdirSync(directory, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name.startsWith('.')) continue;
    if (entry.isDirectory()) {
      if (excludeDirs.has(entry.name)) continue;
      scan(join(directory, entry.name));
      continue;
    }
    if (!entry.isFile()) continue;
    if (!entry.name.endsWith('.ts')) continue;
    if (entry.name.endsWith('.test.ts') || entry.name.endsWith('.spec.ts')) continue;
    if (directory.includes(`${sep}tests${sep}`)) continue;
    if (directory.includes(`${sep}helpers${sep}`) && directory.endsWith(`${sep}helpers`)) {
      continue;
    }

    const absoluteFile = join(directory, entry.name);
    const relativeFile = normalizePath(relative(rootDir, absoluteFile));
    const text = readFileSync(absoluteFile, 'utf8');
    const lines = text.split('\n');
    scanned += 1;
    const hitTables = new Set<string>();
    const addViolation = (lineNumber: number, table: string, lineText: string) => {
      violations.push({
        file: relativeFile,
        line: lineNumber,
        table,
        text: lineText.trim(),
      });
    };

    lines.forEach((line, index) => {
      if (!line.trim()) return;
      const matches = [...line.matchAll(writePattern)];
      for (const match of matches) {
        const table = (match[1] ?? '').replace(/[";]/g, '').toLowerCase();
        if (!writeTargets.includes(table)) continue;
        hitTables.add(table);
        if (!allowlistedWriters.has(relativeFile)) {
          addViolation(index + 1, table, line);
        }
      }
    });

    if (hitTables.size > 0) {
      foundWriteOwners.set(relativeFile, hitTables);
    }
  }
}

function exists(path: string) {
  try {
    return statSync(path).isDirectory();
  } catch {
    return false;
  }
}

function normalizePath(path: string) {
  return relative(rootDir, resolve(rootDir, path))
    .replaceAll('\\', '/');
}
