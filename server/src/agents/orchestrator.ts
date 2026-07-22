import { AGENTS, AgentRoleId } from './registry';
import { runRetrieval } from './retrieval';
import { applyDomainPolicy } from './domain';
import { buildPlan } from './planner';
import { executeCommand } from './executor';
import { verifyResult } from './verifier';
import { makeConversationProvenance } from '../provenance';
import { callOpenAI, callOpenAIStream } from '../providers/openai';
import { createHash } from 'node:crypto';

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

function deterministicStringify(value: unknown): string {
  if (value === null || value === undefined) {
    return String(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((entry) => deterministicStringify(entry)).join(',')}]`;
  }
  if (typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${deterministicStringify((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function deterministicHash(input: unknown): string {
  return createHash('sha256').update(deterministicStringify(input)).digest('hex');
}

function deterministicRunId(input: {
  actor: string;
  conversationId: string;
  domain: string;
  tool: string;
  message: string;
}) {
  return `orch:${input.actor}:${input.conversationId}:${deterministicHash(input).slice(0, 16)}`;
}

function deterministicActionId(input: {
  actor: string;
  conversationId: string;
  domain: string;
  tool: string;
  message: string;
}) {
  return `orch-action:${deterministicHash(input).slice(0, 16)}`;
}

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
  stream?: boolean;
  onModelToken?: (token: string) => void;
  preview?: boolean;
}): Promise<OrchestratedRun> {
  const query = input.message.trim();
  const commandText = input.commandHint ?? query;
  const isPreview = input.preview === true;
  const hasMutatingIntent = /\b(add|create|archive|update|delete|remove|order|buy|purchase)\b/i.test(commandText);
  const executionTool = hasMutatingIntent && !isPreview ? 'chat_execute_command' : 'chat_reply';

  const retrievalRole = await executeAgentRole({
    role: 'retrieval',
    domain: input.domain,
    payload: { query },
  });
  const domainRole = await executeAgentRole({
    role: 'domain',
    domain: input.domain,
    payload: { command: commandText },
  });
  const plannerRole = await executeAgentRole({
    role: 'planner',
    domain: input.domain,
    payload: { command: commandText },
  });

  const retrieval = await runRetrieval({ query, domain: input.domain });
  const sourceIds = [...new Set(retrieval.snapshots.map((snapshot) => snapshot.id).filter(Boolean))];

  const policy = await applyDomainPolicy({
    domain: input.domain,
    command: commandText,
  });

  const executorRole = await executeAgentRole({
    role: 'executor',
    domain: input.domain,
    payload: {
      command: commandText,
      tool: executionTool,
      allowed: policy.allowed,
    },
  });
  const verifierRole = await executeAgentRole({
    role: 'verifier',
    domain: input.domain,
    payload: { action: executionTool, expected: executionTool },
  });

  const roles = [
    toRoleHandoff(retrievalRole as RawRoleResult),
    toRoleHandoff(domainRole as RawRoleResult),
    toRoleHandoff(plannerRole as RawRoleResult),
    toRoleHandoff(executorRole as RawRoleResult),
    toRoleHandoff(verifierRole as RawRoleResult),
    toRoleHandoff({ role: 'orchestrator', status: 'ok' }),
  ];

  const plan = await buildPlan({ command: commandText, domain: input.domain });
  const clarifyingQuestion = policy.requiresClarification ? policy.clarifyingQuestion : undefined;
  const contextSourceText = retrieval.snapshots.length
    ? retrieval.snapshots.map((snapshot) => `${snapshot.label}: ${snapshot.detail}${snapshot.excerpt ? `\nFacts: ${snapshot.excerpt}` : ''}\nSource: ${snapshot.url}`).join('\n')
    : 'No canonical source snapshots available yet.';

  const prompt = `You are Hearth, LifeOS Food planner.
Rules:
- Never invent facts. Ground every claim in provided sources when available.
- When a source is present, answer from its Facts block; do not claim that no source exists.
- Reply with concise, actionable guidance.
- Prefer plain language; when useful, use rows with fields: meal, use, next.
- Only include citations for items drawn from concrete sources.
- If no sources exist, state that explicitly and ask a clarifying follow-up.

Domain: ${input.domain}
Message: ${query}
Context sources:
${contextSourceText}`;
  const ai = input.stream
    ? await callOpenAIStream({
      prompt,
      signal: input.signal,
      previousResponseId: input.previousResponseId,
      onToken: input.onModelToken,
    })
    : await callOpenAI({
      prompt,
      signal: input.signal,
      previousResponseId: input.previousResponseId,
    });

  const commandPlan = !policy.requiresClarification && hasMutatingIntent
    ? plan.steps.find((step) => step.action === 'execute_command')
    : undefined;
  const actionRun = policy.allowed && commandPlan
    ? await executeCommand({
        actionId: deterministicActionId({
          actor: input.actor,
          conversationId: input.conversationId,
          domain: input.domain,
          tool: executionTool,
          message: query,
        }),
        actor: input.actor,
        domain: input.domain,
        tool: executionTool,
        commandText: query,
        record_ids: [],
        conversationId: input.conversationId,
        sourceIds,
        step: commandPlan,
      })
    : undefined;

  const verification = actionRun
    ? await verifyResult({
        actionId: actionRun.receipt.id,
        expected: actionRun.receipt.tool,
        sourceBound: retrieval.snapshots.length > 0,
        expectedSupportsUndo:
          actionRun.receipt.status === 'completed' &&
          actionRun.receipt.tool !== 'chat_reply' &&
          actionRun.receipt.record_ids.length > 0,
      })
    : null;

  const provenance = makeConversationProvenance({
    conversationId: input.conversationId,
    query,
    sources: retrieval.snapshots,
    answerText: ai.text,
  });

  const action = actionRun
    ? {
      state: actionRun.state,
      step: actionRun.step,
      receipt: actionRun.receipt,
      verification,
    }
    : undefined;

  return {
    runId:
      input.runId ??
      deterministicRunId({
        actor: input.actor,
        conversationId: input.conversationId,
        domain: input.domain,
        tool: executionTool,
        message: query,
      }),
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
