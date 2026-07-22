export type SourceCitation = {
  handle: string;
  type: string;
  url: string;
  snapshot_at: string;
};

export function normalizeCitations(citations: SourceCitation[]) {
  return citations.filter((item) => !!item.handle && !!item.url).map((item) => ({
    label: item.handle,
    detail: item.type,
    href: item.url,
    tone: 'moss',
  }));
}

export function makeNoopProvenance(message: string) {
  return {
    answer_text: message,
    sources: [] as ReturnType<typeof normalizeCitations>,
    generated_at: new Date().toISOString(),
  };
}
