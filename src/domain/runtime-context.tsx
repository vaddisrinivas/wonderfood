import { ReactNode, createContext, useContext, useEffect, useState } from 'react';
import type { SQLiteDatabase } from 'expo-sqlite';

import type { AppPackage } from '@/packages/shared/contracts/package';
import {
  activateAppPackage as persistActiveAppPackage,
  getActiveAppPackage,
  rollbackAppPackage as persistRollbackAppPackage,
} from '@/src/db/app-package-registry';
import { type DomainManifest, type ParsedCatalog } from '@/src/domain/catalog';
import { loadAppPackage } from '@/src/domain/package-loader';
import type { AppRuntime } from '@/src/domain/package-runtime';

type AppRuntimeContextValue = {
  db: SQLiteDatabase | null;
  runtime: AppRuntime | null;
  activePackage: AppPackage | null;
  activeManifest: DomainManifest | null;
  catalog: ParsedCatalog | null;
  activateAppPackage(candidate: unknown): Promise<AppPackage>;
  rollbackAppPackage(): Promise<AppPackage | null>;
  refreshRuntime(): Promise<AppRuntime | null>;
};

const AppRuntimeContext = createContext<AppRuntimeContextValue | null>(null);

export function AppRuntimeProvider(
  { db, initialPackage = null, children }: { db: SQLiteDatabase | null; initialPackage?: AppPackage | null; children?: ReactNode },
) {
  const [runtime, setRuntime] = useState<AppRuntime | null>(() => initialPackage ? loadAppPackage(initialPackage) : null);

  useEffect(() => {
    setRuntime(initialPackage ? loadAppPackage(initialPackage) : null);
  }, [initialPackage]);

  useEffect(() => {
    if (!db || initialPackage) {
      return;
    }
    let cancelled = false;
    void getActiveAppPackage(db).then((activePackage) => {
      if (!cancelled) {
        setRuntime(activePackage ? loadAppPackage(activePackage) : null);
      }
    }).catch(() => {
      if (!cancelled) {
        setRuntime(null);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [db, initialPackage]);

  async function refreshRuntime(): Promise<AppRuntime | null> {
    if (!db) {
      return null;
    }
    const activePackage = await getActiveAppPackage(db);
    const nextRuntime = activePackage ? loadAppPackage(activePackage) : null;
    setRuntime(nextRuntime);
    return nextRuntime;
  }

  async function activateRuntimePackage(candidate: unknown): Promise<AppPackage> {
    if (!db) {
      throw new Error('app_runtime_db_unavailable');
    }
    const activePackage = await persistActiveAppPackage(db, candidate);
    const nextRuntime = loadAppPackage(activePackage);
    setRuntime(nextRuntime);
    return nextRuntime.activePackage;
  }

  async function rollbackRuntimePackage(): Promise<AppPackage | null> {
    if (!db) {
      throw new Error('app_runtime_db_unavailable');
    }
    const activePackage = await persistRollbackAppPackage(db);
    const nextRuntime = activePackage ? loadAppPackage(activePackage) : null;
    setRuntime(nextRuntime);
    return nextRuntime?.activePackage ?? null;
  }

  return (
    <AppRuntimeContext.Provider
      value={{
        db,
        runtime,
        activePackage: runtime?.activePackage ?? null,
        activeManifest: runtime?.activeManifest ?? null,
        catalog: runtime?.catalog ?? null,
        activateAppPackage: activateRuntimePackage,
        rollbackAppPackage: rollbackRuntimePackage,
        refreshRuntime,
      }}
    >
      {children}
    </AppRuntimeContext.Provider>
  );
}

export function useAppRuntime(): AppRuntimeContextValue {
  const context = useContext(AppRuntimeContext);
  if (!context) {
    throw new Error('useAppRuntime must be used within AppRuntimeProvider');
  }
  return context;
}
