import { listRecords } from '../mcp/state';
import { pullNotionRecordsLive } from '../providers/notion/pull';
import { pullSheetsRecordsLive } from '../providers/sheets/pull';

export type RetrievalProvider = 'notion' | 'google_sheets';
export type RetrievalFactSensitivity = 'general' | 'personal';

export type RetrievalSnapshot = {
  id: string;
  label: string;
  detail: string;
  url: string;
  tone: 'moss' | 'blue' | 'amber';
  score: number;
  excerpt?: string;
};

export type RetrievalResult = {
  query: string;
  domain: string;
  snapshots: RetrievalSnapshot[];
};

export type RetrievalProjectedFact = {
  field: string;
  sensitivity: RetrievalFactSensitivity;
  value: string;
};

const SECRET_FIELD_PATTERNS = [
  /(^|[._-])(secret|token|api[_-]?key|auth|password|credential|cookie)($|[._-])/i,
  /(^|[._-])(provider[_-]?snapshot|raw[_-]?snapshot|snapshot|json|payload|body|prompt|instruction)($|[._-])/i,
];

const PROMPT_INJECTION_PATTERNS = [
  /\bignore\b.{0,32}\b(instruction|system|previous|developer|tool)\b/i,
  /\b(disregard|override)\b.{0,32}\b(instruction|system|tool)\b/i,
  /\btool[_ -]?call\b/i,
  /<\|/,
];

const ALLOWLISTED_FACT_PATTERNS: Array<{
  pattern: RegExp;
  sensitivity: RetrievalFactSensitivity;
}> = [
  { pattern: /(^|[._-])(status|state|ready|availability)($|[._-])/i, sensitivity: 'general' },
  { pattern: /(^|[._-])(quantity|count|amount|unit|servings)($|[._-])/i, sensitivity: 'general' },
  { pattern: /(^|[._-])(aisle|location|category|type|kind|brand|meal)($|[._-])/i, sensitivity: 'general' },
  { pattern: /(^|[._-])(expires|expires_at|use_by|best_by|updated_at|created_at|scheduled_for)($|[._-])/i, sensitivity: 'personal' },
  { pattern: /(^|[._-])(calories|protein|fat|carbs|fiber|price|cost|currency|minutes|cook_time|prep_time)($|[._-])/i, sensitivity: 'personal' },
];

const PROVIDER_SELECTION_PATTERNS: Record<RetrievalProvider, RegExp[]> = {
  notion: [/\bnotion\b/i, /\bpage\b/i, /\bdatabase\b/i],
  google_sheets: [/\bgoogle\s*sheets\b/i, /\bsheets?\b/i, /\bspreadsheet\b/i, /\bworkbook\b/i],
};

const AUTHORITY_SELECTION_PATTERN = /\b(authority|authoritative|provider|source|live|sync|canonical)\b/i;

function formatCitationDetail(input: {
  collection: string;
  sourceProvider: string;
  sourceExternalId: string;
  updatedAt: string;
}) {
  const parts = [input.collection, input.sourceProvider];
  if (input.sourceExternalId) {
    parts.push(input.sourceExternalId);
  }
  if (input.updatedAt) {
    parts.push(`updated ${input.updatedAt}`);
  }
  return parts.join(' · ');
}

function fallbackRecordUrl(record: { source: { provider: string }; domain: string; id: string }) {
  const encodedId = encodeURIComponent(record.id);
  const encodedDomain = encodeURIComponent(record.domain);

  if (record.source.provider === 'notion') {
    return `wonderfood://notion/record/${encodedDomain}/${encodedId}`;
  }

  if (record.source.provider === 'google_sheets') {
    return `wonderfood://sheets/record/${encodedDomain}/${encodedId}`;
  }

  return `wonderfood://record/${encodedDomain}/${encodedId}`;
}

function toneForProvider(provider: string): 'moss' | 'blue' | 'amber' {
  switch (provider.toLowerCase()) {
    case 'notion':
      return 'moss';
    case 'google_sheets':
      return 'blue';
    default:
      return 'amber';
  }
}

function compactValue(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return value.map(compactValue).filter(Boolean).join(', ');
  if (typeof value === 'object') {
    const candidate = value as Record<string, unknown>;
    for (const key of ['plain_text', 'name', 'content', 'number', 'url']) {
      if (key in candidate) {
        const text = compactValue(candidate[key]);
        if (text) return text;
      }
    }
  }
  return '';
}

function normalizeFactField(field: string): string {
  return field.trim().replace(/\s+/g, '_').replace(/[^A-Za-z0-9_.-]/g, '').toLowerCase();
}

function isSecretField(field: string): boolean {
  return SECRET_FIELD_PATTERNS.some((pattern) => pattern.test(field));
}

function looksLikePromptInjection(text: string): boolean {
  return PROMPT_INJECTION_PATTERNS.some((pattern) => pattern.test(text));
}

function classifyFactField(field: string): RetrievalFactSensitivity | null {
  const normalized = normalizeFactField(field);
  if (!normalized || isSecretField(normalized)) {
    return null;
  }
  const match = ALLOWLISTED_FACT_PATTERNS.find(({ pattern }) => pattern.test(normalized));
  return match?.sensitivity ?? null;
}

function sanitizeFactText(value: unknown): string {
  const text = compactValue(value)
    .replace(/\s+/g, ' ')
    .replace(/[<>{}`]/g, '')
    .trim();
  if (!text || looksLikePromptInjection(text)) {
    return '';
  }
  return text.slice(0, 120);
}

export function projectPromptFacts(properties: Record<string, unknown>): RetrievalProjectedFact[] {
  const out: RetrievalProjectedFact[] = [];
  const visit = (prefix: string, value: unknown, depth: number) => {
    if (depth > 2 || value === null || value === undefined) {
      return;
    }
    if (Array.isArray(value)) {
      const text = sanitizeFactText(value);
      const sensitivity = classifyFactField(prefix);
      if (text && sensitivity) {
        out.push({ field: normalizeFactField(prefix), sensitivity, value: text });
      }
      return;
    }
    if (typeof value === 'object') {
      for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
        const nextPrefix = prefix ? `${prefix}.${key}` : key;
        if (isSecretField(nextPrefix)) {
          continue;
        }
        visit(nextPrefix, nested, depth + 1);
      }
      return;
    }
    const sensitivity = classifyFactField(prefix);
    const text = sanitizeFactText(value);
    if (!sensitivity || !text) {
      return;
    }
    out.push({
      field: normalizeFactField(prefix),
      sensitivity,
      value: text,
    });
  };

  for (const [key, value] of Object.entries(properties)) {
    if (['notion', 'relations', 'unsupported', 'provider_snapshot'].includes(key.toLowerCase())) {
      continue;
    }
    visit(key, value, 0);
  }

  const seen = new Set<string>();
  return out
    .filter((fact) => {
      const key = `${fact.field}:${fact.value}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 8);
}

export function renderPromptFacts(properties: Record<string, unknown>): string {
  return projectPromptFacts(properties)
    .map((fact) => `[${fact.sensitivity}] ${fact.field} = ${fact.value}`)
    .join('\n');
}

export function selectRetrievalProviders(query: string): RetrievalProvider[] {
  const trimmed = query.trim();
  if (!trimmed) {
    return [];
  }

  const selected = (Object.entries(PROVIDER_SELECTION_PATTERNS) as Array<[RetrievalProvider, RegExp[]]>)
    .filter(([, patterns]) => patterns.some((pattern) => pattern.test(trimmed)))
    .map(([provider]) => provider);

  if (selected.length > 0) {
    return selected;
  }

  if (AUTHORITY_SELECTION_PATTERN.test(trimmed)) {
    const authority = process.env.LIFEOS_AUTHORITY_PROVIDER?.trim().toLowerCase();
    if (authority === 'google_sheets') {
      return ['google_sheets'];
    }
    if (authority === 'notion') {
      return ['notion'];
    }
  }

  return [];
}

export async function runRetrieval(input: { query: string; domain: string }): Promise<RetrievalResult> {
  const trimmedQuery = input.query.trim();
  const hasQuery = trimmedQuery.length > 0;
  const recordLimit = 6;
  const recordQuery = hasQuery
    ? listRecords({
        domain: input.domain,
        includeArchived: false,
        query: trimmedQuery,
        limit: recordLimit,
      })
    : listRecords({
        domain: input.domain,
        includeArchived: false,
        limit: recordLimit,
      });

  const localSnapshots = recordQuery
    .map((record, index) => ({
      id: record.id,
      label: record.title || record.id,
      detail: formatCitationDetail({
        collection: record.collection,
        sourceProvider: record.source.provider,
        sourceExternalId: record.source.external_id,
        updatedAt: record.updated_at,
      }),
      url: record.source.url || fallbackRecordUrl(record),
      tone: toneForProvider(record.source.provider),
      score: Number((1 - index * 0.1).toFixed(1)),
      excerpt: renderPromptFacts(record.properties),
    }))
    .filter((snapshot) => snapshot.label.trim().length > 0 && snapshot.id.trim().length > 0);

  const selectedProviders = new Set(selectRetrievalProviders(trimmedQuery));
  const needle = trimmedQuery.toLowerCase();
  const stopWords = new Set(['what', 'which', 'where', 'when', 'does', 'about', 'the', 'this', 'that', 'item', 'canonical', 'please', 'tell', 'show', 'give', 'with', 'from', 'live', 'spreadsheet']);
  const queryTerms = needle.split(/[^a-z0-9_]+/).filter((term) => term.length > 2 && !stopWords.has(term));

  function providerSnapshots(
    records: Array<{ id: string; title: string; collection: string; properties: Record<string, unknown>; source?: Record<string, unknown> }>,
    provider: 'notion' | 'google_sheets',
    detailSuffix: string,
  ): RetrievalSnapshot[] {
    return records
      .map((record, index) => {
        const projectedFacts = projectPromptFacts(record.properties);
        const searchable = [
          record.title,
          record.collection,
          ...projectedFacts.map((fact) => `${fact.field} ${fact.value}`),
        ].join(' ').toLowerCase();
        const matchCount = queryTerms.filter((term) => searchable.includes(term)).length;
        const externalId = typeof record.source?.external_id === 'string' ? record.source.external_id : record.id;
        return {
          id: record.id,
          label: record.title || record.id,
          detail: `${record.collection} · ${detailSuffix} · ${externalId}`,
          url: provider === 'notion'
            ? `wonderfood://notion/page/${encodeURIComponent(record.id)}`
            : `wonderfood://sheets/record/${encodeURIComponent(input.domain)}/${encodeURIComponent(record.id)}`,
          tone: provider === 'notion' ? 'moss' as const : 'blue' as const,
          score: queryTerms.length > 0
            ? Number((matchCount / queryTerms.length + (matchCount > 0 ? 0.1 : 0)).toFixed(2))
            : Number((0.6 - index * 0.05).toFixed(2)),
          matchCount,
          excerpt: projectedFacts
            .map((fact) => `[${fact.sensitivity}] ${fact.field} = ${fact.value}`)
            .join('\n'),
          searchable,
        };
      })
      .filter((snapshot) => queryTerms.length === 0 || snapshot.matchCount > 0)
      .sort((a, b) => b.score - a.score)
      .map(({ searchable: _searchable, matchCount: _matchCount, ...snapshot }) => snapshot);
  }

  const [liveNotion, liveSheets] = await Promise.all([
    selectedProviders.has('notion')
      ? pullNotionRecordsLive({ domain: input.domain, limit: 50 })
      : Promise.resolve({ status: 'disabled' as const, configured: false, records: [], source_snapshots: [], message: 'Not selected for retrieval.' }),
    selectedProviders.has('google_sheets')
      ? pullSheetsRecordsLive({ domain: input.domain })
      : Promise.resolve({ status: 'disabled' as const, configured: false, records: [], source_snapshots: [], message: 'Not selected for retrieval.' }),
  ]);

  const providerSources = [
    ...(liveNotion.status === 'ready' ? providerSnapshots(liveNotion.records, 'notion', 'notion') : []),
    ...(liveSheets.status === 'ready' ? providerSnapshots(liveSheets.records, 'google_sheets', 'google sheets') : []),
  ];

  const dedupedProviderSources = providerSources.filter((snapshot, index, all) => all.findIndex((candidate) => candidate.id === snapshot.id && candidate.tone === snapshot.tone) === index);
  const mergedSources = [...localSnapshots, ...dedupedProviderSources]
    .filter((snapshot, index, all) => all.findIndex((candidate) => candidate.id === snapshot.id && candidate.tone === snapshot.tone) === index)
    // Put exact provider matches ahead of the local fallback rows. This keeps
    // Chat's first source cards and action hints about the thing the user
    // asked for while still retaining local-first context for offline use.
    .sort((a, b) => b.score - a.score || a.label.localeCompare(b.label) || a.tone.localeCompare(b.tone))
    .slice(0, recordLimit);

  if (mergedSources.length > 0) {
    return {
      query: input.query,
      domain: input.domain,
      snapshots: mergedSources,
    };
  }

  if (!hasQuery) {
    return {
      query: input.query,
      domain: input.domain,
      snapshots: [
        {
          id: `domain:${input.domain}`,
          label: input.domain || 'lifeos-domain',
          detail: 'No authority rows yet. Open Sources to connect Notion or Sheets.',
          url: 'wonderfood://app/sources',
          tone: 'moss',
          score: 0.5,
        },
      ],
    };
  }

  return {
    query: input.query,
    domain: input.domain,
    snapshots: [],
  };
}
