import { AGENTS, AgentRoleId } from './registry';
import { runRetrieval } from './retrieval';
import { applyDomainPolicy } from './domain';
import { buildPlan } from './planner';
import { executeCommand } from './executor';
import { verifyResult } from './verifier';
import { makeConversationProvenance, toCitationsFromSnapshots } from '../provenance';
import { callOpenAI } from '../providers/openai';
import { markActionCompleted } from '../actions';

type ExecutorResult = Awaited<ReturnType<typeof executeCommand>>;
type RawRoleResult = { role: AgentRoleId; status: string; reason?: string };
type AgentRoleHandoff = {
  role: AgentRoleId;
  status: 'ok' | 'blocked';
  reason?: string;
};

function toRoleHandoff(roleResult: RawRoleResult): AgentRoleHandoff {
  return {
    role: roleResult.role,
    status: roleResult.status === 'blocked' ? 'blocked' : 'ok',
    reason: roleResult.reason,
  };
}
type OrchestratorAction = {
  state: ExecutorResult['state'];
  step: ExecutorResult['step'];
  receipt: ExecutorResult['receipt'];
  verification: Awaited<ReturnType<typeof verifyResult>> | null;
};

export type OrchestratedRun = {
  runId: string;
  domain: string;
  query: string;
  roles: Array<{
    role: AgentRoleId;
    status: 'ok' | 'blocked';
    reason?: string;
  }>;
  policy: Awaited<ReturnType<typeof applyDomainPolicy>>;
  retrieval: Awaited<ReturnType<typeof runRetrieval>>;
  plan: Awaited<ReturnType<typeof buildPlan>>;
  status: 'ok' | 'clarification' | 'blocked';
  requiresClarification: boolean;
  clarifyingQuestion?: string;
  ai: Awaited<ReturnType<typeof callOpenAI>>;
  action?: OrchestratorAction;
  provenance: ReturnType<typeof makeConversationProvenance>;
};

export async function executeAgentRole(input: {
  role: AgentRoleId;
  domain: string;
  payload: Record<string, unknown>;
}) {
  const manifest = AGENTS[input.role];
  if (!manifest.allowedDomains.includes(input.domain)) {
    return {
      role: input.role,
      status: 'blocked',
      reason: `Domain ${input.domain} not allowed`,
    };
  }

  return {
    role: input.role,
    status: 'ok',
    payload: input.payload,
    concurrency: manifest.concurrency,
    timeoutMs: manifest.timeoutMs,
  };
}

export async function runChatOrchestrator(input: {
  conversationId: string;
  domain: string;
  message: string;
  actor: string;
  commandHint?: string;
  runId?: string;
  signal?: AbortSignal;
  previousResponseId?: string;
}): Promise<OrchestratedRun> {
  const query = input.message.trim();

  const retrievalRole = await executeAgentRole({
    role: 'retrieval',
    domain: input.domain,
    payload: { query },
  });
  const domainRole = await executeAgentRole({
    role: 'domain',
    domain: input.domain,
    payload: { command: input.commandHint ?? query },
  });
  const plannerRole = await executeAgentRole({
    role: 'planner',
    domain: input.domain,
    payload: { command: input.commandHint ?? query },
  });

  const retrieval = await runRetrieval({ query, domain: input.domain });

  const policy = await applyDomainPolicy({
    domain: input.domain,
    command: input.commandHint ?? query,
  });

  const executorRole = await executeAgentRole({
    role: 'executor',
    domain: input.domain,
    payload: {
      command: input.commandHint ?? query,
      tool: 'chat_reply',
      allowed: policy.allowed,
    },
  });
  const verifierRole = await executeAgentRole({
    role: 'verifier',
    domain: input.domain,
    payload: { action: 'chat_reply', expected: 'chat_reply' },
  });

  const roles = [
    toRoleHandoff(retrievalRole as RawRoleResult),
    toRoleHandoff(domainRole as RawRoleResult),
    toRoleHandoff(plannerRole as RawRoleResult),
    toRoleHandoff(executorRole as RawRoleResult),
    toRoleHandoff(verifierRole as RawRoleResult),
    toRoleHandoff({ role: 'orchestrator', status: 'ok' }),
  ];

  const plan = await buildPlan({ command: input.commandHint ?? query, domain: input.domain });
  const clarifyingQuestion = policy.requiresClarification ? policy.clarifyingQuestion : undefined;

  const ai = await callOpenAI({
    prompt: `Domain: ${input.domain}
Message: ${query}
Context sources: ${JSON.stringify(toCitationsFromSnapshots(retrieval.snapshots))}`,
    signal: input.signal,
    previousResponseId: input.previousResponseId,
  });

  const commandPlan = plan.steps.find((step) => step.action === 'execute_command');
  const actionRun = policy.allowed && commandPlan
    ? await executeCommand({
        actionId: `${input.conversationId}:reply:${Date.now()}`,
        actor: input.actor,
        domain: input.domain,
        tool: 'chat_reply',
        record_ids: [],
        step: commandPlan,
      })
    : undefined;

  const verification = actionRun
    ? await verifyResult({
        actionId: actionRun.receipt.id,
        expected: actionRun.receipt.tool,
        sourceBound: retrieval.snapshots.length > 0,
      })
    : null;

  const provenance = makeConversationProvenance({
    conversationId: input.conversationId,
    query,
    sources: retrieval.snapshots,
    answerText: ai.text,
  });

  const completedActionReceipt = actionRun ? markActionCompleted(actionRun.receipt.id) : null;

  const action = actionRun
    ? {
        state: completedActionReceipt?.status ?? actionRun.state,
        step: actionRun.step,
        receipt: completedActionReceipt ?? actionRun.receipt,
        verification,
      }
    : undefined;

  return {
    runId: input.runId ?? `orchestrator:${Date.now()}`,
    domain: input.domain,
    query,
    roles,
    status: policy.requiresClarification ? 'clarification' : 'ok',
    requiresClarification: policy.requiresClarification ?? false,
    clarifyingQuestion,
    policy,
    retrieval,
    plan,
    ai,
    action,
    provenance,
  };
}
