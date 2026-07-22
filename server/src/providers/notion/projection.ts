export type NotionPropertyMap = Record<string, unknown>;

export type NotionCanonicalProjection = {
  id: string;
  domain: string;
  collection: string;
  title: string;
  properties: Record<string, unknown>;
  relations: Array<{ name: string; target_id: string }>;
};

export function toNotionCanonicalProjection(input: {
  id: string;
  domain?: string;
  collection?: string;
  title?: string;
  properties?: NotionPropertyMap;
}) {
  return {
    id: String(input.id || '').trim(),
    domain: String(input.domain || 'food').trim(),
    collection: String(input.collection || 'recipe').trim(),
    title: String(input.title || '').trim(),
    properties: sanitizeNotionProperties(input.properties),
    relations: [],
  } satisfies NotionCanonicalProjection;
}

function sanitizeNotionProperties(properties: NotionPropertyMap | undefined) {
  if (!properties || typeof properties !== 'object') {
    return {};
  }
  return Object.entries(properties).reduce<Record<string, unknown>>((acc, [key, value]) => {
    if (typeof key === 'string' && key.trim()) {
      acc[key.trim()] = value;
    }
    return acc;
  }, {});
}
