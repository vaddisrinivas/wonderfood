export async function runRetrieval(input: { query: string; domain: string }) {
  return {
    query: input.query,
    domain: input.domain,
    snapshots: [
      { kind: 'record', id: 'local-seed', score: 0.92 },
      { kind: 'source', id: 'notion-seed', score: 0.81 },
    ],
  };
}
