import Ajv, { ValidateFunction } from 'ajv';
import { createRequire } from 'node:module';
import type { Operation } from 'fast-json-patch';
import { z, ZodType } from 'zod';

const require = createRequire(import.meta.url);
const jsonPatch = require('fast-json-patch') as typeof import('fast-json-patch');

export const actionEnvelopeSchema = z.object({
  schema_version: z.string().min(1),
  id: z.string().min(1),
  actor: z.string().min(1),
  domain: z.string().min(1),
  tool: z.string().min(1),
  record_ids: z.array(z.string()),
  source_ids: z.array(z.string()),
  idempotency_key: z.string().nullable(),
  operation_id: z.string().min(1),
  cause_id: z.string().min(1),
});

const ajv = new Ajv({ allErrors: true, strict: false });

export function parseWithSchema<T>(schema: ZodType<T>, input: unknown): T {
  return schema.parse(input);
}

export function compileJsonSchema<T>(schema: object): ValidateFunction<T> {
  return ajv.compile(schema) as ValidateFunction<T>;
}

export function validateJsonSchema<T>(schema: object, input: unknown): { valid: true; value: T } | { valid: false; errors: string[] } {
  const validate = compileJsonSchema(schema);
  if (validate(input)) return { valid: true, value: input as T };
  return { valid: false, errors: (validate.errors ?? []).map((error) => `${error.instancePath || '/'} ${error.message ?? 'invalid'}`) };
}

export function diffJson(before: unknown, after: unknown): Operation[] {
  return jsonPatch.compare(before as object, after as object);
}

export function applyJsonDiff<T>(value: T, patch: Operation[]): T {
  const result = jsonPatch.applyPatch(value as object, patch, true, false);
  return result.newDocument as T;
}
