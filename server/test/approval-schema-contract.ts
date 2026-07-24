import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { compileJsonSchema } from '../src/kernel/validation';

type TargetBinding = {
  record_id: string;
  before_revision: number;
  after_revision: number;
  before_version_vector: string;
  after_version_vector: string;
};

type Approval = {
  id: string;
  workspace_id: string;
  actor: string;
  local_actor: string;
  authority: string;
  proposal_id: string;
  action_id: string;
  idempotency_key: string;
  proposal_hash: string;
  operation_hash: string;
  operation_template_hash: string;
  targets: TargetBinding[];
  scope: { workspace_id: string; package_id: string; package_version: string };
  created_at: string;
  expires_at: string;
  decision: { actor: string; outcome: string };
  source: { actor: string; workspace_id: string };
};

function readJson(fileName: string) {
  return JSON.parse(readFileSync(resolve(process.cwd(), 'packages/domain-config/schemas/approval', fileName), 'utf8'));
}

const schema = JSON.parse(
  readFileSync(
    resolve(process.cwd(), 'packages/domain-config/schemas/approval', 'reactive-proposal-approval.v1.schema.json'),
    'utf8',
  ),
);
const validateSchema = compileJsonSchema<Approval>(schema as object);

const validateAsAccepted = (fixtureName: string, input: unknown) => {
  const valid = validateSchema(input);
  if (!valid) {
    const detail = (validateSchema.errors ?? []).map((error) => `${error.instancePath} ${error.message}`).join('; ');
    throw new Error(`${fixtureName}: schema invalid: ${detail}`);
  }
};

const nowMs = Date.now();

const ensureRejectedBySchemaOrBinding = (fixtureName: string, input: unknown) => {
  const valid = validateSchema(input);
  if (valid && checkBinding(input as Approval)) {
    throw new Error(`${fixtureName}: expected threat fixture to fail schema or binding checks`);
  }
};

function checkBinding(approval: Approval): boolean {
  const proposalAnchor = approveAnchor();
  if (seenApprovalIds.has(approval.id)) return false;
  if (approval.id !== approval.id.trim()) return false;
  if (approval.actor !== approval.local_actor) return false;
  if (approval.actor !== approval.decision.actor) return false;
  if (approval.actor !== approval.source.actor) return false;
  if (approval.workspace_id !== approval.source.workspace_id) return false;
  if (approval.workspace_id !== approval.scope.workspace_id) return false;
  if (approval.authority !== `workspace:${approval.workspace_id}`) return false;
  if (approval.proposal_id !== proposalAnchor.proposal_id) return false;
  if (approval.action_id !== proposalAnchor.action_id) return false;
  if (approval.id !== proposalAnchor.id) return false;
  if (approval.idempotency_key !== proposalAnchor.idempotency_key) return false;
  if (approval.proposal_hash !== proposalAnchor.proposal_hash) return false;
  if (approval.operation_hash !== proposalAnchor.operation_hash) return false;
  if (approval.operation_template_hash !== proposalAnchor.operation_template_hash) return false;
  if (approval.decision.outcome !== 'approved') return false;
  if (!approval.scope.package_id || !approval.scope.package_version) return false;
  if (Date.parse(approval.created_at) >= Date.parse(approval.expires_at)) return false;
  if (Date.parse(approval.created_at) > nowMs) return false;
  if (Date.parse(approval.expires_at) <= nowMs) return false;
  for (const target of approval.targets) {
    if (!target.record_id) return false;
    if (target.before_revision > target.after_revision) return false;
  }
  return true;
}

const seenApprovalIds = new Set<string>();
const proposalAnchor = readJson('fixtures/accept.json') as Approval;

function approveAnchor(): Pick<Approval, 'proposal_id' | 'action_id' | 'id' | 'idempotency_key' | 'proposal_hash' | 'operation_hash' | 'operation_template_hash'> {
  return {
    proposal_id: proposalAnchor.proposal_id,
    action_id: proposalAnchor.action_id,
    id: proposalAnchor.id,
    idempotency_key: proposalAnchor.idempotency_key,
    proposal_hash: proposalAnchor.proposal_hash,
    operation_hash: proposalAnchor.operation_hash,
    operation_template_hash: proposalAnchor.operation_template_hash,
  };
}

const threatFixtures = [
  'tampered-idempotency-key.json',
  'tampered-operation-hash.json',
  'tampered-proposal-hash.json',
  'wrong-actor.json',
  'wrong-workspace.json',
  'revision-drift.json',
  'expired.json',
  'capability-escalation.json',
  'action-binding.json',
  'replay.json',
  'ai-sdk-approval.json',
];

validateAsAccepted('accept.json', proposalAnchor);
seenApprovalIds.add(proposalAnchor.id);
if (!checkBinding(proposalAnchor)) throw new Error('accept fixture failed binding checks');

for (const fixtureName of threatFixtures) {
  const fixture = readJson(`fixtures/${fixtureName}`);
  ensureRejectedBySchemaOrBinding(fixtureName, fixture);
}

console.log('approval-schema-contract: passed');
