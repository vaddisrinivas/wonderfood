export type ServerChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  answer?: {
    title: string;
    intro: string;
    rows: Array<{ meal: string; use: string; next: string }>;
    citations: Array<{ label: string; detail: string; href: string; tone: 'moss' | 'blue' | 'amber' }>;
  };
};

export type ServerChatResponse = {
  conversation_id: string;
  messages: ServerChatMessage[];
  thread: { id: string; title: string; detail: string };
  warnings?: string[];
};

const baselineCitations = [
  { label: 'App snapshot', detail: 'Local canonical graph', href: 'wonderfood://app/snapshot', tone: 'moss' as const },
  { label: 'LifeOS Notion', detail: 'Template + source links', href: 'https://app.notion.com', tone: 'blue' as const },
  { label: 'LifeOS Sheets', detail: 'Projection and formulas', href: 'https://docs.google.com/spreadsheets', tone: 'amber' as const },
];

export function makeServerAnswer(text: string) {
  const lower = text.toLowerCase();
  const focus = lower.includes('shop') || lower.includes('buy')
    ? 'A focused grocery pass'
    : lower.includes('yogurt') || lower.includes('expire')
      ? 'Use yogurt first'
      : 'A practical kitchen next step';

  return {
    title: focus,
    intro: 'I used the active Food context and available sources to keep this practical.',
    rows: lower.includes('shop') || lower.includes('buy')
      ? [
          { meal: 'Produce', use: 'Coriander, lemons', next: 'Supports tonight and green dal.' },
          { meal: 'Dairy', use: 'Greek yogurt', next: 'Finish before Friday.' },
          { meal: 'Pantry', use: 'Naan', next: 'Add only if needed.' },
        ]
      : [
          { meal: 'Tonight', use: 'Tandoori chicken', next: 'Use yogurt in the marinade.' },
          { meal: 'Tomorrow', use: 'Green dal', next: 'Pair with remaining spinach.' },
          { meal: 'Breakfast', use: 'Yogurt bowl', next: 'Finish before Friday.' },
        ],
    citations: baselineCitations,
  };
}

export async function handleServerChat(input: {
  conversationId: string;
  message: string;
  threadTitle?: string;
}): Promise<ServerChatResponse> {
  const now = Date.now();
  const assistantId = `server-${now}-asst`;
  const answer = makeServerAnswer(input.message);

  return {
    conversation_id: input.conversationId,
    messages: [{
      id: assistantId,
      role: 'assistant',
      text: `I processed: ${input.message}. This is a typed response from the server stub.`,
      answer,
    }],
    thread: {
      id: input.conversationId,
      title: input.threadTitle || 'Food context',
      detail: 'live response channel',
    },
  };
}
