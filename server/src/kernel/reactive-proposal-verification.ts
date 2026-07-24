import type { OperationTemplate } from './package';
import type { McpRecord } from '../mcp/state';

export type ReactiveProposalVerificationReceipt = Readonly<{
  ok: boolean;
  verifierVersion: 'wonder.reactive-proposal-verifier.v1';
  recordId: string | null;
  expected: Record<string, unknown>;
  observed: Record<string, unknown> | null;
  reason: string;
}>;

export function verifyReactiveProposalPostcondition(input: {
  operationTemplate: OperationTemplate;
  record: McpRecord | null | undefined;
  beforeRevision?: number;
}): ReactiveProposalVerificationReceipt {
  const template = input.operationTemplate;
  if (template.kind === 'custom') {
    return receipt(false, null, {}, null, 'custom_operation_has_no_canonical_postcondition');
  }
  if (template.kind === 'restore_record') {
    return receipt(false, template.recordId, {}, observedRecord(input.record), 'restore_postcondition_requires_undo_context');
  }
  if (template.kind === 'create_record') {
    const recordId = template.recordId ?? input.record?.id ?? null;
    const expected = {
      exists: true,
      collection: template.collection,
      properties: template.properties ?? {},
    };
    const observed = observedRecord(input.record);
    const ok = Boolean(input.record)
      && input.record?.collection === template.collection
      && hasProperties(input.record.properties, template.properties ?? {});
    return receipt(ok, recordId, expected, observed, ok ? 'canonical_create_verified' : 'canonical_create_mismatch');
  }
  if (template.kind === 'update_record') {
    const observed = observedRecord(input.record);
    const expected = {
      exists: true,
      properties: template.changes,
      revisionAfter: input.beforeRevision === undefined ? undefined : input.beforeRevision + 1,
    };
    const ok = Boolean(input.record)
      && hasProperties(input.record?.properties ?? {}, template.changes)
      && (input.beforeRevision === undefined || input.record?.revision === input.beforeRevision + 1);
    return receipt(ok, template.recordId, expected, observed, ok ? 'canonical_update_verified' : 'canonical_update_mismatch');
  }
  const observed = observedRecord(input.record);
  const ok = Boolean(input.record?.archived_at);
  return receipt(ok, template.recordId, { archived: true }, observed, ok ? 'canonical_archive_verified' : 'canonical_archive_mismatch');
}

function hasProperties(observed: Record<string, unknown>, expected: Record<string, unknown>): boolean {
  return Object.entries(expected).every(([key, value]) => Object.is(observed[key], value));
}

function observedRecord(record: McpRecord | null | undefined): Record<string, unknown> | null {
  if (!record) return null;
  return {
    id: record.id,
    collection: record.collection,
    archived: Boolean(record.archived_at),
    revision: record.revision ?? null,
    properties: record.properties,
  };
}

function receipt(
  ok: boolean,
  recordId: string | null,
  expected: Record<string, unknown>,
  observed: Record<string, unknown> | null,
  reason: string,
): ReactiveProposalVerificationReceipt {
  return {
    ok,
    verifierVersion: 'wonder.reactive-proposal-verifier.v1',
    recordId,
    expected,
    observed,
    reason,
  };
}
