import { useEffect, useState } from 'react';

import { useLifeOSDatabase } from '@/src/db/provider';
import { getProviderSyncSummary, type ProviderSyncSummary } from '@/src/db/provider-status';
import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { queryDomainRecords } from '@/src/domain/queries';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { JsonRenderSurface } from '@/src/presentation/json-render-surface';
import { ROUTE_SHELL_UI } from '@/src/presentation/a2ui-route-surfaces';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

type JsonRenderRouteProps = {
  screen: string;
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  useDomainUi?: boolean;
  emptyTitle?: string;
  recordId?: string;
};

export function JsonRenderRoute({ screen, eyebrow, title, subtitle, useDomainUi, emptyTitle, recordId }: JsonRenderRouteProps) {
  const db = useLifeOSDatabase();
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeManifest } = loadCatalog();
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);
  const [providerSync, setProviderSync] = useState<ProviderSyncSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    void queryDomainRecords(db).then((items) => {
      if (!cancelled) setRecords(recordId ? items.filter((item) => item.id === recordId) : items);
    }).catch(() => {
      if (!cancelled) setRecords([]);
    });
    return () => {
      cancelled = true;
    };
  }, [db, recordId, settings.runtime.activeDomain]);

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
  }, [db, screen, settings.runtime.activeDomain]);
  const activeUi = activeManifest.ui?.screens?.[screen] ? activeManifest.ui : ROUTE_SHELL_UI;

  return (
    <JsonRenderSurface
      eyebrow={eyebrow ?? activeManifest.label.toUpperCase()}
      title={title ?? activeManifest.label}
      subtitle={subtitle}
      ui={useDomainUi ? activeManifest.ui : activeUi}
      screen={screen}
      records={records}
      nativePermissions={activeManifest.native_capabilities?.permissions}
      providerSync={providerSync}
      emptyTitle={emptyTitle}
    />
  );
}
