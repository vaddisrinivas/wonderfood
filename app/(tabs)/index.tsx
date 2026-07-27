import { useEffect, useState } from 'react';

import { useLifeOSDatabase } from '@/src/db/provider';
import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { queryDomainRecords } from '@/src/domain/queries';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { JsonRenderSurface } from '@/src/presentation/json-render-surface';
import { HOME_SHELL_UI } from '@/src/presentation/shell-ui';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

export default function HomeScreen() {
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
      eyebrow="WONDER"
      title="Today"
      subtitle={`${activeManifest.label} is active. Dinner, pantry, shopping, and AI help in one calm place.`}
      ui={HOME_SHELL_UI}
      records={records}
    />
  );
}
