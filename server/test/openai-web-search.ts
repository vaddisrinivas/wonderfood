import { callOpenAI, callOpenAIStream } from '../src/providers/openai';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

function ensure(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

const originalFetch = globalThis.fetch;
const previousKey = process.env.OPENAI_API_KEY;
process.env.OPENAI_API_KEY = 'web-search-contract-token';

const calls: Array<{ body: Record<string, unknown> }> = [];

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

try {
  globalThis.fetch = (async (_input: string | URL, init: RequestInit = {}) => {
    const body = JSON.parse(String(init.body || '{}')) as Record<string, unknown>;
    calls.push({ body });
    if (body.stream === true) {
      const stream = [
        'data: ' + JSON.stringify({ type: 'response.output_text.delta', delta: '{"title":"Fresh answer","rows":[]}' }) + '\n\n',
        'data: ' + JSON.stringify({
          type: 'response.output_item.done',
          item: {
            type: 'message',
            content: [{
              type: 'output_text',
              annotations: [{ type: 'url_citation', url: 'https://example.com/fresh', title: 'Fresh source' }],
            }],
          },
        }) + '\n\n',
        'data: [DONE]\n\n',
      ].join('');
      return new Response(stream, {
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
      });
    }
    return jsonResponse({
      id: 'resp-web-contract',
      output_text: '{"title":"Fresh answer","rows":[]}',
      output: [{
        type: 'message',
        content: [{
          type: 'output_text',
          annotations: [{ type: 'url_citation', url: 'https://example.com/fresh', title: 'Fresh source' }],
        }],
      }],
    });
  }) as typeof globalThis.fetch;

  const direct = await callOpenAI({ prompt: 'What is the latest food safety guidance?', webSearch: true });
  ensure(direct.status === 'ok', 'Expected direct Responses call to succeed');
  ensure(Array.isArray(direct.webCitations) && direct.webCitations[0]?.url === 'https://example.com/fresh', 'Expected direct URL citation extraction');
  const directTool = (calls[0]?.body.tools as Array<Record<string, unknown>> | undefined)?.[0];
  ensure(directTool?.type === 'web_search', 'Expected web_search tool in direct request');

  const noSearch = await callOpenAI({ prompt: 'Use my pantry snapshot.', webSearch: false });
  ensure(noSearch.status === 'ok', 'Expected no-search Responses call to succeed');
  ensure(!('tools' in calls[1].body), 'Unexpected web_search tool on disabled request');

  const streamed = await callOpenAIStream({ prompt: 'What changed today?', webSearch: true });
  ensure(streamed.status === 'ok', 'Expected streamed Responses call to succeed');
  ensure(streamed.webCitations?.[0]?.url === 'https://example.com/fresh', 'Expected streamed URL citation extraction');
  ensure(Array.isArray(calls[2]?.body.tools), 'Expected web_search tool in streamed request');

  const evidenceDir = join(process.cwd(), 'app', 'build', 'evidence', 'phase3-web-search');
  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(join(evidenceDir, 'phase3-web-search-proof.json'), JSON.stringify({
    proof: 'phase3_web_search_citations',
    direct_url: direct.webCitations?.[0]?.url,
    streamed_url: streamed.webCitations?.[0]?.url,
    direct_tool: directTool?.type,
    streamed_tool: (calls[2]?.body.tools as Array<Record<string, unknown>> | undefined)?.[0]?.type,
    all_passed: true,
  }, null, 2));
  console.log('PASS server/test/openai-web-search.ts');
} finally {
  globalThis.fetch = originalFetch;
  if (previousKey === undefined) delete process.env.OPENAI_API_KEY;
  else process.env.OPENAI_API_KEY = previousKey;
}
