import { expect, test } from 'vitest';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import {
  SPARK_PROMPT_NAME,
  SPARK_READ_TOOL_NAME,
  SPARK_RESOURCE_URI,
  SPARK_TOOL_NAME,
  MCP_ACCESS_TOKEN,
  createMcpSpikeServer,
} from '../src/server.js';

type JsonRpc = {
  jsonrpc: '2.0';
  id: number;
  method: string;
  params?: {
    protocolVersion: string;
    capabilities: Record<string, unknown>;
    clientInfo: {
      name: string;
      version: string;
    };
  };
};

function textResultContent(result: unknown): string {
  const value = result as {
    content?: Array<{ text?: string }>;
  };
  return value?.content?.[0]?.text ?? '{}';
}

async function withClient(token: string | undefined, run: (client: Client) => Promise<void>) {
  const server = await createMcpSpikeServer({
    authRequired: Boolean(token),
  });

  const transport = new StreamableHTTPClientTransport(new URL(server.url), {
    requestInit: {
      headers: token
        ? {
            Authorization: `Bearer ${token}`,
          }
        : undefined,
    },
  });

  const client = new Client(
    {
      name: 'utopia-mcp-spike-client',
      version: '0.1.0',
    },
    {
      capabilities: {},
    },
  );

  try {
    await client.connect(transport);
    await run(client);
  } finally {
    await client.close().catch(() => undefined);
    await server.close();
  }
}

test('MCP Streamable HTTP initialize/list/call + JSON response and proposal output', async () => {
  await withClient(MCP_ACCESS_TOKEN, async (client) => {
    const tools = await client.listTools();
    expect(tools.tools.some((tool: { name: string }) => tool.name === SPARK_TOOL_NAME)).toBe(true);
    expect(
      tools.tools.some((tool: { name: string }) => tool.name === SPARK_READ_TOOL_NAME),
    ).toBe(true);

    const resources = await client.listResources();
    expect(
      resources.resources.some(
        (resource: { uri: string }) => resource.uri === SPARK_RESOURCE_URI,
      ),
    ).toBe(true);

    const prompts = await client.listPrompts();
    expect(
      prompts.prompts.some(
        (prompt: { name: string }) => prompt.name === SPARK_PROMPT_NAME,
      ),
    ).toBe(true);

    const proposal = await client.callTool({
      name: SPARK_TOOL_NAME,
      arguments: {
        title: 'Lunch prep',
        collection: 'food',
      },
    });

    const proposalText = JSON.parse(textResultContent(proposal));
    expect(proposalText.reviewOnly).toBe(true);
    expect(proposalText.mode).toBe('proposal_only');

    const read = await client.callTool({
      name: SPARK_READ_TOOL_NAME,
      arguments: {
        id: 'abc-123',
      },
    });

    const readText = JSON.parse(textResultContent(read));
    expect(readText.reviewOnly).toBe(false);
    expect(readText.snapshot).toBe('record:abc-123');
  });
});

test('MCP bearer auth is enforced and raw streamable call returns JSON', async () => {
  const server = await createMcpSpikeServer({
    authRequired: true,
  });

  const initPayload: JsonRpc = {
    jsonrpc: '2.0',
    id: 1,
    method: 'initialize',
    params: {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: {
        name: 'raw-http-spike',
        version: '0.1.0',
      },
    },
  };

  const unauth = await fetch(server.url, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      accept: 'application/json, text/event-stream',
    },
    body: JSON.stringify(initPayload),
  });

  expect(unauth.status).toBe(401);

  const auth = await fetch(server.url, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      accept: 'application/json, text/event-stream',
      Authorization: `Bearer ${MCP_ACCESS_TOKEN}`,
    },
    body: JSON.stringify(initPayload),
  });

  const contentType = auth.headers.get('content-type') || '';
  expect(contentType).toContain('application/json');
  expect(auth.status).toBe(200);

  await server.close();
});
