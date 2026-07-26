import { mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, basename } from 'node:path';

function ensureDir(path: string) {
  mkdirSync(dirname(path), { recursive: true });
}

function quarantinePath(path: string) {
  const extension = extname(path);
  const stem = extension ? basename(path, extension) : basename(path);
  const suffix = `${Date.now()}-${process.pid}`;
  return join(dirname(path), `${stem}.corrupt-${suffix}${extension || '.json'}`);
}

export function readJsonStateFile<T>(path: string, input: {
  label: string;
  validate: (value: unknown) => value is T;
}): T {
  try {
    const parsed = JSON.parse(readFileSync(path, 'utf-8')) as unknown;
    if (!input.validate(parsed)) {
      throw new Error(`${input.label} schema validation failed`);
    }
    return parsed;
  } catch (error) {
    const quarantined = quarantinePath(path);
    try {
      renameSync(path, quarantined);
    } catch (renameError) {
      const reason = renameError instanceof Error ? renameError.message : String(renameError);
      throw new Error(`Corrupt ${input.label} at ${path}; quarantine failed: ${reason}`);
    }
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Corrupt ${input.label} at ${path}; quarantined to ${quarantined}: ${detail}`);
  }
}

export function writeJsonStateFileAtomic(path: string, value: unknown) {
  ensureDir(path);
  const tmpPath = join(dirname(path), `.${basename(path)}.tmp-${process.pid}-${Date.now()}`);
  writeFileSync(tmpPath, JSON.stringify(value, null, 2), 'utf-8');
  try {
    renameSync(tmpPath, path);
  } finally {
    rmSync(tmpPath, { force: true });
  }
}
