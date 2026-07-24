import { SQLiteDatabase } from 'expo-sqlite';

export type ConversationRole = 'user' | 'assistant';

export type Message = {
  id: string;
  role: ConversationRole;
  sort_index: number;
  body: string;
  answer_payload: string | null;
  created_at: string;
};

export type Conversation = {
  id: string;
  domain: string;
  title: string;
  detail: string;
  created_at: string;
  updated_at: string;
  archived_at: string | null;
};

export type ConversationEnvelope = Conversation & {
  messages: Message[];
};

type ConversationRow = Omit<Conversation, 'messages'>;
type MessageRow = Message;

export async function listConversations(
  db: SQLiteDatabase,
  domain: string,
  includeArchived = false
): Promise<Conversation[]> {
  const whereClause = includeArchived ? 'domain = ? ORDER BY updated_at DESC' : 'domain = ? AND archived_at IS NULL ORDER BY updated_at DESC';
  return db.getAllAsync<ConversationRow>(`SELECT * FROM conversations WHERE ${whereClause}`, [domain]);
}

export async function getConversation(db: SQLiteDatabase, conversationId: string): Promise<ConversationEnvelope | null> {
  const conversation = await db.getFirstAsync<ConversationRow>('SELECT * FROM conversations WHERE id = ?', [conversationId]);
  if (!conversation) return null;

  const messages = await db.getAllAsync<MessageRow>(
    'SELECT id, role, sort_index, body, answer_payload, created_at FROM conversation_messages WHERE conversation_id = ? ORDER BY sort_index ASC',
    [conversationId]
  );

  return {
    ...conversation,
    messages,
  };
}

export async function createConversation(
  db: SQLiteDatabase,
  input: {
    id: string;
    domain: string;
    title: string;
    detail: string;
  }
): Promise<Conversation> {
  const now = new Date().toISOString();
  await db.runAsync(
    `
      INSERT INTO conversations (id, domain, title, detail, created_at, updated_at, archived_at)
      VALUES (?, ?, ?, ?, ?, ?, NULL)
    `,
    [input.id, input.domain, input.title, input.detail, now, now]
  );
  return {
    id: input.id,
    domain: input.domain,
    title: input.title,
    detail: input.detail,
    created_at: now,
    updated_at: now,
    archived_at: null,
  };
}

export async function upsertConversation(
  db: SQLiteDatabase,
  input: {
    id: string;
    domain: string;
    title: string;
    detail: string;
  }
): Promise<Conversation> {
  const now = new Date().toISOString();
  await db.runAsync(
    `
      INSERT INTO conversations (id, domain, title, detail, created_at, updated_at, archived_at)
      VALUES (?, ?, ?, ?, ?, ?, NULL)
      ON CONFLICT(id) DO UPDATE SET
        title = excluded.title,
        detail = excluded.detail,
        updated_at = excluded.updated_at,
        domain = excluded.domain
    `,
    [input.id, input.domain, input.title, input.detail, now, now]
  );

  return {
    id: input.id,
    domain: input.domain,
    title: input.title,
    detail: input.detail,
    created_at: now,
    updated_at: now,
    archived_at: null,
  };
}

export async function appendMessage(
  db: SQLiteDatabase,
  input: {
    id: string;
    conversation_id: string;
    role: ConversationRole;
    sort_index: number;
    body: string;
    answer_payload?: unknown;
  }
): Promise<void> {
  const now = new Date().toISOString();
  const answerPayload = input.answer_payload ? JSON.stringify(input.answer_payload) : null;
  await db.runAsync(
    `
      INSERT INTO conversation_messages (id, conversation_id, role, sort_index, body, answer_payload, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `,
    [input.id, input.conversation_id, input.role, input.sort_index, input.body, answerPayload, now]
  );
  await db.runAsync(
    'UPDATE conversations SET updated_at = ? WHERE id = ?',
    [now, input.conversation_id]
  );
}
