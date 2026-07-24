/**
 * MCP protocol compatibility kept independent from the business/tool layer.
 * The custom transport can use this until the official SDK transport is
 * adopted; callers still get deterministic version negotiation today.
 */
export const MCP_PROTOCOL_VERSIONS = [
  '2026-03-11',
  '2025-11-25',
  '2025-06-18',
  '2025-03-26',
  '2024-11-05',
] as const;

export type McpProtocolVersion = (typeof MCP_PROTOCOL_VERSIONS)[number];

export function negotiateMcpProtocolVersion(requested: unknown): McpProtocolVersion {
  if (typeof requested === 'string' && (MCP_PROTOCOL_VERSIONS as readonly string[]).includes(requested)) {
    return requested as McpProtocolVersion;
  }
  return MCP_PROTOCOL_VERSIONS[0];
}

export function isMcpProtocolVersion(value: unknown): value is McpProtocolVersion {
  return typeof value === 'string' && (MCP_PROTOCOL_VERSIONS as readonly string[]).includes(value);
}

const LOCAL_ORIGINS = new Set(['http://localhost', 'http://127.0.0.1', 'http://[::1]']);

/** Browser Origin guard for the custom HTTP adapter. Non-browser clients may omit Origin. */
export function isAllowedMcpOrigin(origin: unknown, configured: readonly string[] = []): boolean {
  if (origin === undefined || origin === null || origin === '') return true;
  if (typeof origin !== 'string') return false;
  const allowed = configured.length > 0 ? configured : [...LOCAL_ORIGINS];
  return allowed.includes(origin);
}
