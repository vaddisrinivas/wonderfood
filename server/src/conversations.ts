import type { ServerChatMessage } from './chat';

type ConversationEnvelope = {
  id: string;
  domain: string;
  messages: ServerChatMessage[];
  title: string;
  detail: string;
};

const conversations = new Map<string, ConversationEnvelope>();

export function getConversation(id: string): ConversationEnvelope | null {
  return conversations.get(id) ?? null;
}

export function upsertConversation(conversation: Omit<ConversationEnvelope, 'messages'>): ConversationEnvelope {
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
  return next;
}

export function appendServerMessage(id: string, message: ServerChatMessage): ConversationEnvelope {
  const conversation = getConversation(id);
  if (!conversation) {
    throw new Error('Conversation not found');
  }
  conversation.messages.push(message);
  conversations.set(id, conversation);
  return conversation;
}

export function listConversations() {
  return [...conversations.values()];
}

export function ensureConversation(id: string, domain: string, fallbackTitle: string): ConversationEnvelope {
  const existing = getConversation(id);
  if (existing) {
    return existing;
  }

  return upsertConversation({
    id,
    domain,
    title: fallbackTitle || 'New conversation',
    detail: `${domain} context`,
  });
}
