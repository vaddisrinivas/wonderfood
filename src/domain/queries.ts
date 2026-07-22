import { SQLiteDatabase } from 'expo-sqlite';

import { loadCatalog } from '@/src/domain/catalog';
import { CanonicalRecord } from '@/src/domain/runtime';
import { getRecord, listRecordsByCollections, listRecordsForDomain } from '@/src/db/records';
import { getAllProviderLinks } from '@/src/db/sources';
import { foodRecords, sourceRows } from '@/src/data/sample';
import { toRecordView, DomainRecordViewModel, recordsToViews } from '@/src/domain/renderer';
import { buildSurfaceCatalog } from '@/src/domain/surface';

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
    freshness: link.freshness ?? 'Demo snapshot',
    workspace: link.workspace,
  };
}

function fallbackFoodCards() {
  return foodRecords.map((record) => ({
    id: record.id,
    collection: 'sample',
    title: record.title,
    body: record.body,
    status: record.status,
    tone: record.tone,
    source: record.source,
    meta: record.meta,
  }));
}

function fallbackSourceRows(): SourceRow[] {
  return sourceRows.map(([name, status, freshness, workspace]) => ({
    name,
    status,
    freshness,
    workspace,
  }));
}

export function getActiveDomainFeed() {
  const catalog = loadCatalog();
  return {
    domainId: catalog.activeDomainId,
    manifest: catalog.activeManifest,
    catalog,
  };
}

export async function queryDomainCollections(
  db: SQLiteDatabase | null,
  collections: string[]
): Promise<DomainRecordViewModel[]> {
  if (!db) {
    return fallbackFoodCards();
  }

  const { domainId, manifest } = getActiveDomainFeed();
  const records: CanonicalRecord[] = collections.length
    ? await listRecordsByCollections(db, domainId, collections)
    : await listRecordsForDomain(db, domainId);
  if (!records.length) {
    return recordsToViews(records);
  }

  return recordsToViews(records)
    .filter((item) => !item.collection || manifest.collections.includes(item.collection) || collections.includes(item.collection));
}

export async function queryDomainRecords(
  db: SQLiteDatabase | null
): Promise<DomainRecordViewModel[]> {
  const { domainId } = getActiveDomainFeed();
  if (!db) {
    return fallbackFoodCards();
  }

  const records = await listRecordsForDomain(db, domainId);
  return recordsToViews(records);
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
    const fallback = fallbackFoodCards().find((record) => record.id === id);
    return fallback ?? null;
  }

  const { domainId } = getActiveDomainFeed();
  const record = await getRecord(db, id);
  if (!record || record.domain !== domainId) return null;
  return toRecordView(record);
}

export async function getDomainRecordCanonical(
  db: SQLiteDatabase | null,
  id: string
): Promise<CanonicalRecord | null> {
  if (!db) {
    return null;
  }

  const { domainId } = getActiveDomainFeed();
  const record = await getRecord(db, id);
  if (!record || record.domain !== domainId) {
    return null;
  }

  return record;
}

export async function listSourceRows(db: SQLiteDatabase | null): Promise<SourceRow[]> {
  if (!db) {
    return fallbackSourceRows();
  }

  const links = await getAllProviderLinks(db);
  const mapped = links.map(mapSourceRowFromLink);
  if (mapped.length > 0) {
    return mapped;
  }

  return fallbackSourceRows();
}
export type { DomainRecordViewModel };
