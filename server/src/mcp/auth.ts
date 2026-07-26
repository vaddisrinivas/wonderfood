export type HeaderMap = Record<string, string | string[] | undefined>;

export type RequestAuthorizationResult = {
  ok: boolean;
  localDevelopment: boolean;
  statusCode: 200 | 401 | 503;
  message: string;
};

export type McpScope = {
  domains: Set<string>;
  principal: string | null;
};

const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);
const DOMAIN_SCOPE_HEADER_CANDIDATES = ['x-lifeos-domain-scope', 'x-lifeos-tenant-scope'] as const;
const PRINCIPAL_SCOPE_HEADER_CANDIDATES = ['x-lifeos-principal', 'x-lifeos-principal-scope'] as const;
const DOMAIN_SCOPE_ENTRY_RE = /^[A-Za-z0-9_.:-]+$/;

export const LOCAL_DEVELOPMENT_ENV = 'LIFEOS_LOCAL_DEV';

function serverAuthToken(): string {
  return process.env.LIFEOS_SERVER_TOKEN?.trim() || '';
}

function mcpAuthToken(): string {
  return process.env.LIFEOS_MCP_TOKEN?.trim() || serverAuthToken();
}

function firstHeaderValue(headers: HeaderMap, name: string): string | undefined {
  const rawHeader = headers?.[name];
  if (!rawHeader) {
    return undefined;
  }
  if (Array.isArray(rawHeader)) {
    return typeof rawHeader[0] === 'string' ? rawHeader[0].trim() : undefined;
  }
  return String(rawHeader).trim();
}

function parseBoolean(raw: unknown): boolean {
  return typeof raw === 'string' && TRUE_VALUES.has(raw.trim().toLowerCase());
}

function normalizeScopeEntries(raw: string | undefined): Set<string> {
  if (!raw) {
    return new Set();
  }
  return new Set(
    raw
      .split(',')
      .map((entry) => entry.trim().toLowerCase())
      .filter((entry) => entry.length > 0 && DOMAIN_SCOPE_ENTRY_RE.test(entry)),
  );
}

export function isExplicitLocalDevelopment(): boolean {
  return parseBoolean(process.env[LOCAL_DEVELOPMENT_ENV]);
}

export function getBearerToken(headers: HeaderMap): string | undefined {
  return firstHeaderValue(headers, 'authorization');
}

export function authorizeBearerRequest(headers: HeaderMap, token: string, label: string): RequestAuthorizationResult {
  if (isExplicitLocalDevelopment()) {
    return {
      ok: true,
      localDevelopment: true,
      statusCode: 200,
      message: `${label} auth bypassed in explicit local-development mode.`,
    };
  }

  if (!token) {
    return {
      ok: false,
      localDevelopment: false,
      statusCode: 503,
      message: `${label} token not configured. Set ${label === 'MCP' ? 'LIFEOS_MCP_TOKEN or ' : ''}LIFEOS_SERVER_TOKEN, or explicitly enable ${LOCAL_DEVELOPMENT_ENV}=true for local development.`,
    };
  }

  const bearer = getBearerToken(headers);
  if (!bearer) {
    return {
      ok: false,
      localDevelopment: false,
      statusCode: 401,
      message: `Missing ${label.toLowerCase()} bearer token`,
    };
  }

  if (bearer !== `Bearer ${token}`) {
    return {
      ok: false,
      localDevelopment: false,
      statusCode: 401,
      message: `Invalid ${label.toLowerCase()} bearer token`,
    };
  }

  return {
    ok: true,
    localDevelopment: false,
    statusCode: 200,
    message: `${label} authorized`,
  };
}

export function authorizeServerRequest(headers: HeaderMap): RequestAuthorizationResult {
  return authorizeBearerRequest(headers, serverAuthToken(), 'Server');
}

export function authorizeMcpRequest(headers: HeaderMap): RequestAuthorizationResult {
  return authorizeBearerRequest(headers, mcpAuthToken(), 'MCP');
}

export function isMcpToolAuthorized(headers: HeaderMap): boolean {
  return authorizeMcpRequest(headers).ok;
}

export function canExposeProviderStatusIds(headers: HeaderMap): boolean {
  return authorizeServerRequest(headers).ok;
}

export function parseMcpScope(headers: HeaderMap): McpScope {
  const domainHeader = DOMAIN_SCOPE_HEADER_CANDIDATES
    .map((name) => firstHeaderValue(headers, name))
    .find((value) => typeof value === 'string' && value.length > 0);
  const principalHeader = PRINCIPAL_SCOPE_HEADER_CANDIDATES
    .map((name) => firstHeaderValue(headers, name))
    .find((value) => typeof value === 'string' && value.length > 0);

  return {
    domains: normalizeScopeEntries(domainHeader),
    principal: principalHeader?.trim() || null,
  };
}
