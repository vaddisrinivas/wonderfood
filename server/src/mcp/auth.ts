export const MCP_SERVER_TOKEN = process.env.LIFEOS_MCP_TOKEN ?? process.env.LIFEOS_SERVER_TOKEN;

export function getBearerToken(headers: Record<string, string | string[] | undefined>): string | undefined {
  const rawHeader = headers?.authorization;
  if (!rawHeader) {
    return undefined;
  }
  if (Array.isArray(rawHeader)) {
    return typeof rawHeader[0] === 'string' ? rawHeader[0].trim() : undefined;
  }
  return String(rawHeader).trim();
}

export function isMcpToolAuthorized(headers: Record<string, string | string[] | undefined>): boolean {
  if (!MCP_SERVER_TOKEN) {
    return true;
  }
  const token = getBearerToken(headers);
  return token === `Bearer ${MCP_SERVER_TOKEN}`;
}

