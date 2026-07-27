import { useEffect, useState } from 'react';

import { useLifeOSDatabase } from '@/src/db/provider';
import { getProviderSyncSummary, type ProviderSyncSummary } from '@/src/db/provider-status';
import { listRecordsForDomain } from '@/src/db/records';
import { recordsToViews, type DomainRecordViewModel } from '@/src/domain/renderer';
import { useAppRuntime } from '@/src/domain/runtime-context';
import { JsonRenderSurface } from '@/src/presentation/json-render-surface';

type JsonRenderRouteProps = {
  screen: string;
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  emptyTitle?: string;
  recordId?: string;
};

export function JsonRenderRoute({ screen, eyebrow, title, subtitle, emptyTitle, recordId }: JsonRenderRouteProps) {
  const db = useLifeOSDatabase();
  const { activeManifest, activePackage, catalog } = useAppRuntime();
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);
  const [providerSync, setProviderSync] = useState<ProviderSyncSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    const domainId = catalog?.activeDomainId ?? activeManifest?.id ?? null;
    if (!db || !domainId) {
      setRecords([]);
      return () => {
        cancelled = true;
      };
    }
    void listRecordsForDomain(db, domainId).then((items) => {
      if (!cancelled) {
        const next = recordsToViews(items);
        setRecords(recordId ? next.filter((item) => item.id === recordId) : next);
      }
    }).catch(() => {
      if (!cancelled) {
        setRecords([]);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [activeManifest?.id, catalog?.activeDomainId, db, recordId]);

  useEffect(() => {
    let cancelled = false;
    void getProviderSyncSummary(db).then((summary) => {
      if (!cancelled) setProviderSync(summary);
    }).catch(() => {
      if (!cancelled) setProviderSync(null);
    });
    return () => {
      cancelled = true;
    };
  }, [db, screen]);
  return (
    <JsonRenderSurface
      eyebrow={eyebrow}
      title={title ?? activeManifest?.label ?? 'App'}
      subtitle={subtitle}
      ui={activeManifest?.ui}
      screen={screen}
      records={records}
      nativePermissions={activePackage?.schemaVersion === 'wonder.app-package.v3'
        ? activePackage.nativeCapabilities.permissions
        : activeManifest?.native_capabilities?.permissions}
      providerSync={providerSync}
      emptyTitle={emptyTitle}
    />
  );
}
