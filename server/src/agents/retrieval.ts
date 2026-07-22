import { listRecords } from '../mcp/state';
import { pullNotionRecordsLive } from '../providers/notion/pull';

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

function compactFacts(properties: Record<string, unknown>): string {
  return Object.entries(properties)
    .filter(([key]) => !['notion', 'relations'].includes(key.toLowerCase()))
    .map(([key, value]) => {
      const text = compactValue(value);
      return text ? `${key}: ${text}` : '';
    })
    .filter(Boolean)
    .slice(0, 12)
    .join('; ');
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

  const snapshots = recordQuery
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
      excerpt: compactFacts(record.properties),
    }))
    .filter((snapshot) => snapshot.label.trim().length > 0 && snapshot.id.trim().length > 0);

  if (snapshots.length > 0) {
    return {
      query: input.query,
      domain: input.domain,
      snapshots,
    };
  }

  // A fresh client can have no local MCP rows yet. If Notion is configured,
  // retrieve the canonical data source before telling Chat that nothing exists.
  // Webhook events remain change signals; this is an on-demand read path only.
  const liveNotion = await pullNotionRecordsLive({ domain: input.domain, limit: 50 });
  if (liveNotion.status === 'ready' && liveNotion.records.length > 0) {
    const needle = trimmedQuery.toLowerCase();
    const stopWords = new Set(['what', 'which', 'where', 'when', 'does', 'about', 'the', 'this', 'that', 'item', 'canonical', 'please', 'tell', 'show', 'give', 'with', 'from']);
    const queryTerms = needle.split(/[^a-z0-9_]+/).filter((term) => term.length > 2 && !stopWords.has(term));
    const liveSnapshots = liveNotion.records
      .map((record, index) => {
        const searchable = `${record.title} ${record.collection} ${JSON.stringify(record.properties)}`.toLowerCase();
        const matchCount = queryTerms.filter((term) => searchable.includes(term)).length;
        return {
          id: record.id,
          label: record.title || record.id,
          detail: `${record.collection} · notion · ${liveNotion.source_snapshots[index] && typeof liveNotion.source_snapshots[index] === 'object' && 'data_source_id' in (liveNotion.source_snapshots[index] as Record<string, unknown>) ? String((liveNotion.source_snapshots[index] as Record<string, unknown>).data_source_id) : 'canonical data source'}`,
          url: `wonderfood://notion/page/${encodeURIComponent(record.id)}`,
          tone: 'moss' as const,
          score: queryTerms.length > 0
            ? Number((matchCount / queryTerms.length + (matchCount > 0 ? 0.1 : 0)).toFixed(2))
            : Number((0.6 - index * 0.05).toFixed(2)),
          matchCount,
          excerpt: compactFacts(record.properties),
          searchable,
        };
      })
      .filter((snapshot) => queryTerms.length === 0 || snapshot.matchCount > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, recordLimit)
      .map(({ searchable: _searchable, matchCount: _matchCount, ...snapshot }) => snapshot);

    if (liveSnapshots.length > 0) {
      return {
        query: input.query,
        domain: input.domain,
        snapshots: liveSnapshots,
      };
    }
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
