import type { ServerChatMessage } from './chat';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

const DEFAULT_CONVERSATION_OWNER = 'server';

type PersistedConversationEnvelope = {
  id: string;
  owner?: string;
  domain: string;
  messages: ServerChatMessage[];
  title: string;
  detail: string;
  last_response_id?: string;
};

type PersistedFile = {
  version: 1;
  updated_at: string;
  conversations: PersistedConversationEnvelope[];
};

const STORAGE_PATH =
  process.env.LIFEOS_CHAT_CONVERSATIONS_PATH ??
  join(process.cwd(), 'server-data', 'conversations.json');

const STORE_VERSION = 1;
const FILE_ENCODING = 'utf-8';

type ConversationEnvelope = {
  id: string;
  domain: string;
  messages: ServerChatMessage[];
  title: string;
  detail: string;
  last_response_id?: string;
};

type StoredConversationEnvelope = ConversationEnvelope & {
  owner: string;
};

const conversations = new Map<string, StoredConversationEnvelope>();
let isLoaded = false;

function ensureDir() {
  mkdirSync(dirname(STORAGE_PATH), { recursive: true });
}

function deepClone<T>(value: T): T {
  return value === undefined ? value : JSON.parse(JSON.stringify(value)) as T;
}

function storageKey(id: string, owner: string) {
  return `${owner}\u0000${id}`;
}

function normalizeOwner(owner?: string): string {
  return typeof owner === 'string' && owner.trim().length > 0
    ? owner.trim()
    : DEFAULT_CONVERSATION_OWNER;
}

function cloneConversation(conversation: StoredConversationEnvelope): ConversationEnvelope {
  return {
    id: conversation.id,
    domain: conversation.domain,
    messages: deepClone(conversation.messages),
    title: conversation.title,
    detail: conversation.detail,
    ...(conversation.last_response_id ? { last_response_id: conversation.last_response_id } : {}),
  };
}

function persist() {
  ensureDir();
  const payload: PersistedFile = {
    version: STORE_VERSION,
    updated_at: new Date().toISOString(),
    conversations: [...conversations.values()].map((conversation) => ({
      id: conversation.id,
      owner: conversation.owner,
      domain: conversation.domain,
      messages: deepClone(conversation.messages),
      title: conversation.title,
      detail: conversation.detail,
      ...(conversation.last_response_id ? { last_response_id: conversation.last_response_id } : {}),
    })),
  };
  writeFileSync(STORAGE_PATH, JSON.stringify(payload), FILE_ENCODING);
}

function load() {
  if (isLoaded) {
    return;
  }
  isLoaded = true;

  if (!existsSync(STORAGE_PATH)) {
    return;
  }

  try {
    const raw = readFileSync(STORAGE_PATH, FILE_ENCODING);
    const parsed = JSON.parse(raw) as unknown;
    if (!isValidPersistedFile(parsed)) {
      return;
    }

    for (const row of parsed.conversations) {
      if (!isConversationRow(row)) {
        continue;
      }
      const owner = normalizeOwner(row.owner);
      conversations.set(storageKey(row.id, owner), {
        id: row.id,
        owner,
        domain: row.domain,
        messages: deepClone(row.messages ?? []),
        title: row.title,
        detail: row.detail,
        ...(row.last_response_id ? { last_response_id: row.last_response_id } : {}),
      });
    }
  } catch {
    return;
  }
}

function isConversationRow(value: unknown): value is PersistedConversationEnvelope {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const row = value as Record<string, unknown>;
  return (
    typeof row.id === 'string'
    && typeof row.domain === 'string'
    && typeof row.title === 'string'
    && typeof row.detail === 'string'
    && (Array.isArray(row.messages))
  );
}

function isValidPersistedFile(value: unknown): value is PersistedFile {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const parsed = value as { version?: number; conversations?: unknown };
  return (
    (typeof parsed.version === 'undefined' || parsed.version === STORE_VERSION)
    && Array.isArray(parsed.conversations)
  );
}

function getStoredConversation(id: string, owner?: string): StoredConversationEnvelope | null {
  load();
  return conversations.get(storageKey(id, normalizeOwner(owner))) ?? null;
}

export function getConversation(id: string, owner?: string): ConversationEnvelope | null {
  load();
  const conversation = getStoredConversation(id, owner);
  return conversation ? cloneConversation(conversation) : null;
}

export function upsertConversation(
  conversation: Omit<ConversationEnvelope, 'messages'>,
  owner?: string,
): ConversationEnvelope {
  load();
  const normalizedOwner = normalizeOwner(owner);
  const key = storageKey(conversation.id, normalizedOwner);
  const existing = conversations.get(key);
  const next: StoredConversationEnvelope = existing
    ? {
      ...existing,
      ...conversation,
    }
    : {
      ...conversation,
      owner: normalizedOwner,
      messages: [],
    };
  conversations.set(key, next);
  persist();
  return cloneConversation(next);
}

export function appendServerMessage(id: string, message: ServerChatMessage, owner?: string): ConversationEnvelope {
  load();
  const conversation = getStoredConversation(id, owner);
  if (!conversation) {
    throw new Error('Conversation not found');
  }
  conversation.messages.push(deepClone(message));
  conversations.set(storageKey(id, conversation.owner), conversation);
  persist();
  return cloneConversation(conversation);
}

export function setConversationResponseId(id: string, responseId: string, owner?: string) {
  load();
  const conversation = getStoredConversation(id, owner);
  if (!conversation || !responseId.trim()) {
    return;
  }
  conversation.last_response_id = responseId.trim();
  conversations.set(storageKey(id, conversation.owner), conversation);
  persist();
}

export function listConversations(owner?: string) {
  load();
  const normalizedOwner = normalizeOwner(owner);
  return [...conversations.values()]
    .filter((conversation) => conversation.owner === normalizedOwner)
    .map(cloneConversation);
}

export function ensureConversation(
  id: string,
  domain: string,
  fallbackTitle: string,
  owner?: string,
): ConversationEnvelope {
  load();
  const existing = getConversation(id, owner);
  if (existing) {
    return existing;
  }

  return upsertConversation({
    id,
    domain,
    title: fallbackTitle || 'New conversation',
    detail: `${domain} context`,
  }, owner);
}
