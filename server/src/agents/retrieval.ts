export type RetrievalSnapshot = {
  id: string;
  label: string;
  detail: string;
  url: string;
  tone: 'moss' | 'blue' | 'amber';
  score: number;
};

export type RetrievalResult = {
  query: string;
  domain: string;
  snapshots: RetrievalSnapshot[];
};

const FALLBACK_SEED_SNAPSHOTS: RetrievalSnapshot[] = [
  {
    id: 'seed:app-snapshot',
    label: 'App snapshot',
    detail: 'Canonical graph for active Food context',
    url: 'wonderfood://app/snapshot',
    tone: 'moss',
    score: 0.96,
  },
  {
    id: 'seed:notion',
    label: 'LifeOS Notion',
    detail: 'Template + relations',
    url: 'https://app.notion.com',
    tone: 'blue',
    score: 0.93,
  },
];

export async function runRetrieval(input: { query: string; domain: string }): Promise<RetrievalResult> {
  const normalized = input.query.trim().toLowerCase();
  const fallback = [...FALLBACK_SEED_SNAPSHOTS];

  if (normalized.includes('sheet') || normalized.includes('sheets')) {
    fallback.push({
      id: 'seed:sheets',
      label: 'LifeOS Sheets',
      detail: 'Projection health and formula surface',
      url: 'https://docs.google.com/spreadsheets',
      tone: 'amber',
      score: 0.88,
    });
  }

  return {
    query: input.query,
    domain: input.domain,
    snapshots: fallback.sort((a, b) => b.score - a.score),
  };
}
