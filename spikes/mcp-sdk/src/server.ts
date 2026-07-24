import http from 'node:http';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';

export const MCP_ACCESS_TOKEN = 'spike-token-1';
export const AUTH_HEADER = 'authorization';

export const SPARK_TOOL_NAME = 'wonderfood.propose.record';
export const SPARK_READ_TOOL_NAME = 'wonderfood.read.record';
export const SPARK_RESOURCE_URI = 'spike://resources/contract';
export const SPARK_PROMPT_NAME = 'wonderfood.proposal-review';

export type McpSpikeServer = {
  close: () => Promise<void> | void;
  url: string;
  port: number;
};

function createConfiguredMcpServer(): McpServer {
  const server = new McpServer(
    {
      name: 'wonderfood-utopia-mcp-spike',
      version: '1.0.0',
    },
    {
      capabilities: {
        tools: {},
        resources: {},
        prompts: {},
      },
    },
  );

  server.registerTool(
    SPARK_TOOL_NAME,
    {
      title: 'Proposal-only record mutation',
      description: 'Returns a typed mutation proposal payload only; no canonical write occurs here.',
      inputSchema: {
        title: z.string().min(1),
        collection: z.string().min(1),
      },
    },
    async (args: { title: string; collection: string }) => {
      const { title, collection } = args;
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              reviewOnly: true,
              mode: 'proposal_only',
              title,
              collection,
            }),
          },
        ],
      };
    },
  );

  server.registerTool(
    SPARK_READ_TOOL_NAME,
    {
      title: 'Read-only record lookup',
      description: 'Returns a static, non-mutating payload.',
      inputSchema: {
        id: z.string().min(1),
      },
    },
    async (args: { id: string }) => {
      const { id } = args;
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              id,
              reviewOnly: false,
              snapshot: `record:${id}`,
            }),
          },
        ],
      };
    },
  );

  server.registerResource(
    'mcp_contract',
    SPARK_RESOURCE_URI,
    {
      description: 'Spike resource with mutation contract guidance',
      mimeType: 'application/json',
    },
    async () => {
      return {
        contents: [
          {
            uri: SPARK_RESOURCE_URI,
            text: JSON.stringify(
              {
                proposalOnly: true,
                mode: 'stateless',
                capabilities: {
                  tools: true,
                  resources: true,
                  prompts: true,
                },
              },
              null,
              2,
            ),
            mimeType: 'application/json',
          },
        ],
      };
    },
  );

  server.registerPrompt(
    SPARK_PROMPT_NAME,
    {
      description: 'Prompt for safe proposal review guidance.',
      argsSchema: {
        draft: z.string().optional(),
      },
    },
    ({ draft }: { draft?: string }) => {
      const resolvedDraft = typeof draft === 'string' ? draft : 'empty';
      return {
        messages: [
          {
            role: 'user',
            content: {
              type: 'text',
              text: `Review mutation draft with Wonder law: ${resolvedDraft}`,
            },
          },
        ],
      };
    },
  );

  return server;
}

export async function createMcpSpikeServer({
  authRequired,
  port = 0,
}: {
  authRequired: boolean;
  port?: number;
}): Promise<McpSpikeServer> {
  const httpServer = http.createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ status: 'ok', mode: 'stateless' }));
      return;
    }

    if (req.url !== '/mcp') {
      res.writeHead(404, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ jsonrpc: '2.0', error: 'not_found' }));
      return;
    }

    if (authRequired) {
      const auth = req.headers[AUTH_HEADER];
      if (auth !== `Bearer ${MCP_ACCESS_TOKEN}`) {
        res.writeHead(401, {
          'content-type': 'application/json',
          'www-authenticate': 'Bearer',
        });
        res.end(JSON.stringify({ error: 'unauthorized' }));
        return;
      }
    }

    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
      enableJsonResponse: true,
    });
    const server = createConfiguredMcpServer();

    await server.connect(transport);
    await transport.handleRequest(req, res);
    await server.close();
  });

  await new Promise<void>((resolve, reject) => {
    httpServer.once('error', reject);
    httpServer.listen(port, resolve);
  });

  const actualPort = (httpServer.address() as { port: number }).port;

  return {
    close: async () => {
      await new Promise<void>((resolve, reject) => {
        httpServer.close((error) => {
          if (error) {
            reject(error);
            return;
          }
          resolve();
        });
      });
    },
    port: actualPort,
    url: `http://127.0.0.1:${actualPort}/mcp`,
  };
}
