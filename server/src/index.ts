import { handleServerChat } from './chat';
import { ensureConversation, appendServerMessage, upsertConversation } from './conversations';

declare const require: any;

const { createServer } = require('http');
const port = Number(process.env.PORT ?? '8787');

function json(value: unknown) {
  return JSON.stringify(value);
}

function unauthorized(res: any, message: string) {
  res.statusCode = 401;
  res.setHeader('content-type', 'application/json');
  res.end(json({ status: 'error', message }));
}

function badRequest(res: any, message: string) {
  res.statusCode = 400;
  res.setHeader('content-type', 'application/json');
  res.end(json({ status: 'error', message }));
}

function ok(res: any, body: unknown) {
  res.statusCode = 200;
  res.setHeader('content-type', 'application/json');
  res.end(json(body));
}

const server = createServer(async (req: any, res: any) => {
  if (req.method === 'GET' && req.url === '/health') {
    ok(res, { status: 'ok', service: 'wonderfood-lifeos-server' });
    return;
  }

  if (!req.url || !req.url.startsWith('/chat')) {
    badRequest(res, 'Route not found');
    return;
  }

  if (req.method === 'POST' && req.url === '/chat/send') {
    const auth = req.headers.authorization;
    const token = process.env.LIFEOS_SERVER_TOKEN;
    if (token && auth !== `Bearer ${token}`) {
      unauthorized(res, 'Invalid server token');
      return;
    }

    const chunks = await new Promise<string>((resolve, reject) => {
      let data = '';
      req.on('data', (chunk: string) => {
        data += chunk;
      });
      req.on('end', () => resolve(data));
      req.on('error', reject);
    });

    let parsed: { conversation_id?: string; message?: { text?: string; id?: string }; domain_id?: string };
    try {
      parsed = JSON.parse(chunks || '{}');
    } catch {
      badRequest(res, 'Invalid JSON');
      return;
    }

    const conversationId = parsed.conversation_id || `server-${Date.now()}`;
    const text = typeof parsed.message?.text === 'string' ? parsed.message.text : '';
    const conversation = ensureConversation(conversationId, parsed.domain_id || 'food', text.slice(0, 80));
    upsertConversation({ id: conversation.id, domain: conversation.domain, title: conversation.title, detail: conversation.detail });

    const response = await handleServerChat({
      conversationId,
      message: text,
      threadTitle: conversation.title,
    });

    for (const message of response.messages) {
      appendServerMessage(conversationId, message);
    }

    ok(res, response);
    return;
  }

  badRequest(res, 'Unsupported method');
});

server.listen(port, '127.0.0.1', () => {
  console.log(`[server] listening on http://127.0.0.1:${port}`);
});

export { server };
