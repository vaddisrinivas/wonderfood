import { useEffect, useState } from 'react';

import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { queryDomainRecords } from '@/src/domain/queries';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { JsonRenderSurface } from '@/src/presentation/json-render-surface';

export default function FoodScreen() {
  const db = useLifeOSDatabase();
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeManifest } = loadCatalog();
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);

  useEffect(() => {
    let cancelled = false;
    void queryDomainRecords(db).then((items) => {
      if (!cancelled) setRecords(items);
    }).catch(() => {
      if (!cancelled) setRecords([]);
    });
    return () => {
      cancelled = true;
    };
  }, [db, settings.runtime.activeDomain]);

  return (
    <JsonRenderSurface
      eyebrow={activeManifest.label.toUpperCase()}
      title={activeManifest.label}
      subtitle="Dinner, pantry, shopping, and review — focused for food."
      ui={activeManifest.ui}
      records={records}
    />
  );
}
