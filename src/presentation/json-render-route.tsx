import { useEffect, useState } from 'react';

import { useLifeOSDatabase } from '@/src/db/provider';
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

  return (
    <JsonRenderSurface
      eyebrow={eyebrow ?? activeManifest.label.toUpperCase()}
      title={title ?? activeManifest.label}
      subtitle={subtitle}
      ui={useDomainUi ? activeManifest.ui : ROUTE_SHELL_UI}
      screen={screen}
      records={records}
      emptyTitle={emptyTitle}
    />
  );
}
