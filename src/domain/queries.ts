import { SQLiteDatabase } from 'expo-sqlite';

import { loadCatalog } from '@/src/domain/catalog';
import { CanonicalRecord, validateCanonicalRecord } from '@/src/domain/runtime';
import { getRecord, listRecordsByCollections, listRecordsForDomain } from '@/src/db/records';
import { getAllProviderLinks } from '@/src/db/sources';
import { toRecordView, DomainRecordViewModel, recordsToViews } from '@/src/domain/renderer';
import { buildSurfaceCatalog } from '@/src/domain/surface';
import { sampleRecordsAsCanonical } from '@/src/data/sample';

export type DomainRecordFeed = {
  domainId: string;
  manifest: ReturnType<typeof loadCatalog>['activeManifest'];
};

export type SourceRow = {
  name: string;
  status: string;
  freshness: string;
  workspace: string | null;
};

function mapSourceRowFromLink(link: Awaited<ReturnType<typeof getAllProviderLinks>>[number]): SourceRow {
  return {
    name: link.provider,
    status: link.status ?? 'Configured',
    freshness: link.freshness ?? 'Local snapshot',
    workspace: link.workspace,
  };
}

export function getActiveDomainFeed() {
  const catalog = loadCatalog();
  return {
    domainId: catalog.activeDomainId,
    manifest: catalog.activeManifest,
    catalog,
  };
}

function sampleRecordsForActiveDomain(): CanonicalRecord[] {
  const { domainId, manifest } = getActiveDomainFeed();
  return domainId === 'food'
    ? sampleRecordsAsCanonical(domainId).map((record) => validateCanonicalRecord(record, domainId, manifest, 'sample'))
    : [];
}

export async function queryDomainCollections(
  db: SQLiteDatabase | null,
  collections: string[]
): Promise<DomainRecordViewModel[]> {
  if (!db) {
    const fallback = sampleRecordsForActiveDomain();
    return recordsToViews(collections.length ? fallback.filter((record) => collections.includes(record.collection)) : fallback);
  }

  const { domainId, manifest } = getActiveDomainFeed();
  const records: CanonicalRecord[] = collections.length
    ? await listRecordsByCollections(db, domainId, collections)
    : await listRecordsForDomain(db, domainId);
  if (!records.length) {
    const fallback = sampleRecordsForActiveDomain();
    return recordsToViews(collections.length ? fallback.filter((record) => collections.includes(record.collection)) : fallback);
  }

  return recordsToViews(records)
    .filter((item) => !item.collection || manifest.collections.includes(item.collection) || collections.includes(item.collection));
}

export async function queryDomainRecords(
  db: SQLiteDatabase | null
): Promise<DomainRecordViewModel[]> {
  const { domainId } = getActiveDomainFeed();
  if (!db) {
    return recordsToViews(sampleRecordsForActiveDomain());
  }

  const records = await listRecordsForDomain(db, domainId);
  return recordsToViews(records.length ? records : sampleRecordsForActiveDomain());
}

export async function searchDomainRecords(db: SQLiteDatabase | null, query: string): Promise<DomainRecordViewModel[]> {
  if (!query.trim()) {
    return [];
  }

  const records = await queryDomainRecords(db);
  const normalized = query.toLowerCase();
  return records.filter((item) => [item.title, item.collection, item.body, item.meta].join(' ').toLowerCase().includes(normalized));
}

export function getSurfaceCollectionsForLabel(label: string): string[] {
  const { manifest } = getActiveDomainFeed();
  const catalog = buildSurfaceCatalog(manifest);
  const surface = catalog.byLabel.get(label);
  return surface ? surface.collections : [];
}

export async function getDomainRecord(db: SQLiteDatabase | null, id: string): Promise<DomainRecordViewModel | null> {
  if (!db) {
    const fallback = sampleRecordsForActiveDomain().find((record) => record.id === id);
    return fallback ? toRecordView(fallback) : null;
  }

  const { domainId } = getActiveDomainFeed();
  const record = await getRecord(db, id);
  if (!record || record.domain !== domainId) {
    const fallback = sampleRecordsForActiveDomain().find((item) => item.id === id);
    return fallback ? toRecordView(fallback) : null;
  }
  return toRecordView(record);
}

export async function getDomainRecordCanonical(
  db: SQLiteDatabase | null,
  id: string
): Promise<CanonicalRecord | null> {
  if (!db) {
    return sampleRecordsForActiveDomain().find((record) => record.id === id) ?? null;
  }

  const { domainId } = getActiveDomainFeed();
  const record = await getRecord(db, id);
  if (!record || record.domain !== domainId) {
    return sampleRecordsForActiveDomain().find((item) => item.id === id) ?? null;
  }

  return record;
}

export async function listSourceRows(db: SQLiteDatabase | null): Promise<SourceRow[]> {
  if (!db) {
    const fallback = sampleRecordsForActiveDomain();
    return fallback.length
      ? [{
          name: 'sqlite',
          status: 'Local ready',
          freshness: 'Bundled Food records',
          workspace: `${fallback.length} local records`,
        }]
      : [];
  }

  const links = await getAllProviderLinks(db);
  const mapped = links.map(mapSourceRowFromLink);
  const { domainId } = getActiveDomainFeed();
  const records = await listRecordsForDomain(db, domainId);
  if (!records.length) return mapped;

  const latest = records
    .map((record) => record.updated_at || record.created_at)
    .filter(Boolean)
    .sort()
    .at(-1);

  const localRow = {
    name: 'sqlite',
    status: 'Local ready',
    freshness: latest ? `Updated ${new Date(latest).toLocaleDateString()}` : 'Seeded local records',
    workspace: `${records.length} local records`,
  };

  return mapped.some((row) => row.name.toLowerCase() === 'sqlite') ? mapped : [localRow, ...mapped];
}
export type { DomainRecordViewModel };
