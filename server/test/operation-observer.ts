import assert from 'node:assert/strict';
import { setOperationCommitFailureObserver, setOperationCommitObserver } from '../src/kernel/operation-observer';
import { createRecordWithAction, deleteRecord } from '../src/mcp/state';

const events: unknown[] = [];
setOperationCommitObserver((event) => events.push(event));
const result = createRecordWithAction({
  actionId: 'observer-action',
  actor: 'test',
  domain: 'food',
  tool: 'create_record',
  risk: 'low',
  command: 'create observer proof',
  record: { id: 'observer-record', domain: 'food', collection: 'recipe', title: 'Observer proof', properties: {}, relations: [], source: { provider: 'user', external_id: 'observer-record', url: null, observed_at: new Date().toISOString(), content_hash: null }, archived_at: null },
});
setOperationCommitObserver(null);
assert.equal(events.length, 1);
const event = events[0] as { actionId: string; operationId: string; recordId: string; before: unknown; after: unknown };
assert.equal(event.actionId, result.action.id);
assert.equal(event.operationId, result.action.operation_id);
assert.equal(event.recordId, 'observer-record');
assert.equal(event.before, null);
assert.equal((event.after as { id: string }).id, 'observer-record');
deleteRecord('observer-record');

const failures: Array<{ phase: string; event: { operationId: string }; error: { message: string } }> = [];
setOperationCommitFailureObserver((failure) => failures.push(failure));
setOperationCommitObserver(() => {
  throw Object.assign(new Error('observer exploded'), { phase: 'commit_cycle' });
});
const failureResult = createRecordWithAction({
  actionId: 'observer-failure-action',
  actor: 'test',
  domain: 'food',
  tool: 'create_record',
  risk: 'low',
  command: 'create observer failure proof',
  record: { id: 'observer-failure-record', domain: 'food', collection: 'recipe', title: 'Observer failure proof', properties: {}, relations: [], source: { provider: 'user', external_id: 'observer-failure-record', url: null, observed_at: new Date().toISOString(), content_hash: null }, archived_at: null },
});
setOperationCommitObserver(null);
setOperationCommitFailureObserver(null);
assert.equal(failureResult.action.status, 'completed');
assert.equal(failures.length, 1);
assert.equal(failures[0]?.phase, 'commit_cycle');
assert.equal(failures[0]?.event.operationId, failureResult.action.operation_id);
assert.equal(failures[0]?.error.message, 'observer exploded');
deleteRecord('observer-failure-record');
console.log('operation-observer: passed');
