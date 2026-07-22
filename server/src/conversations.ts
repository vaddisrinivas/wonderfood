import type { ServerChatMessage } from './chat';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

type PersistedConversationEnvelope = {
  id: string;
  domain: string;
  messages: ServerChatMessage[];
  title: string;
  detail: string;
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
};

const conversations = new Map<string, ConversationEnvelope>();
let isLoaded = false;

function ensureDir() {
  mkdirSync(dirname(STORAGE_PATH), { recursive: true });
}

function cloneConversation(conversation: ConversationEnvelope): ConversationEnvelope {
  return {
    id: conversation.id,
    domain: conversation.domain,
    messages: [...conversation.messages],
    title: conversation.title,
    detail: conversation.detail,
  };
}

function persist() {
  ensureDir();
  const payload: PersistedFile = {
    version: STORE_VERSION,
    updated_at: new Date().toISOString(),
    conversations: [...conversations.values()],
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
      conversations.set(row.id, {
        id: row.id,
        domain: row.domain,
        messages: row.messages ?? [],
        title: row.title,
        detail: row.detail,
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

export function getConversation(id: string): ConversationEnvelope | null {
  load();
  return conversations.get(id) ?? null;
}

export function upsertConversation(conversation: Omit<ConversationEnvelope, 'messages'>): ConversationEnvelope {
  load();
  const existing = conversations.get(conversation.id);
  const next: ConversationEnvelope = existing
    ? {
      ...existing,
      ...conversation,
    }
    : {
      ...conversation,
      messages: [],
    };
  conversations.set(conversation.id, next);
  persist();
  return next;
}

export function appendServerMessage(id: string, message: ServerChatMessage): ConversationEnvelope {
  load();
  const conversation = getConversation(id);
  if (!conversation) {
    throw new Error('Conversation not found');
  }
  conversation.messages.push(message);
  conversations.set(id, conversation);
  persist();
  return conversation;
}

export function listConversations() {
  load();
  return [...conversations.values()].map(cloneConversation);
}

export function ensureConversation(id: string, domain: string, fallbackTitle: string): ConversationEnvelope {
  load();
  const existing = getConversation(id);
  if (existing) {
    return cloneConversation(existing);
  }

  return upsertConversation({
    id,
    domain,
    title: fallbackTitle || 'New conversation',
    detail: `${domain} context`,
  });
}
