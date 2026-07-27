import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

import { listMcpResources, readMcpResource } from '../resources/catalog';
import { callMcpTool, listMcpTools } from '../tools/catalog';
import type { McpScope } from '../security/auth';

const MCP_SERVER_NAME = 'wonderfood-lifeos-server';
const MCP_SERVER_VERSION = '1.0.0';

function jsonText(value: unknown) {
  return {
    content: [
      {
        type: 'text' as const,
        text: JSON.stringify(value),
      },
    ],
  };
}

export function createWonderMcpSdkServer(scope: McpScope) {
  const server = new McpServer({
    name: MCP_SERVER_NAME,
    version: MCP_SERVER_VERSION,
  });

  server.registerTool(
    'wonderfood.status',
    {
      title: 'WonderFood status',
      description: 'Return the WonderFood MCP tool/resource status.',
      inputSchema: {},
    },
    async () => jsonText({ status: 'ready', server: MCP_SERVER_NAME }),
  );

  server.registerTool(
    'wonderfood.tools',
    {
      title: 'WonderFood tool catalog',
      description: 'List WonderFood MCP tools available to the scoped principal.',
      inputSchema: {},
    },
    async () => jsonText(listMcpTools()),
  );

  server.registerTool(
    'wonderfood.call_tool',
    {
      title: 'WonderFood scoped tool call',
      description: 'Call a WonderFood MCP tool through the existing policy and proposal boundary.',
      inputSchema: {
        name: z.string(),
        arguments: z.record(z.string(), z.unknown()).optional(),
      },
    },
    async ({ name, arguments: args }: { name: string; arguments?: Record<string, unknown> }) => {
      return jsonText(await callMcpTool(name, args ?? {}));
    },
  );

  server.registerTool(
    'wonderfood.resources',
    {
      title: 'WonderFood resource catalog',
      description: 'List WonderFood MCP resources available to the scoped principal.',
      inputSchema: {},
    },
    async () => jsonText(listMcpResources()),
  );

  server.registerTool(
    'wonderfood.read_resource',
    {
      title: 'WonderFood scoped resource read',
      description: 'Read a WonderFood MCP resource through existing resource authorization.',
      inputSchema: {
        uri: z.string(),
      },
    },
    async ({ uri }: { uri: string }) => jsonText(readMcpResource(uri)),
  );

  return server;
}
