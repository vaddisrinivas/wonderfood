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

export async function runRetrieval(input: { query: string; domain: string }): Promise<RetrievalResult> {
  return {
    query: input.query,
    domain: input.domain,
    snapshots: [],
  };
}
