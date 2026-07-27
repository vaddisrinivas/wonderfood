import { ReactNode, createContext, useContext, useEffect, useState } from 'react';
import { SQLiteDatabase, openDatabaseAsync } from 'expo-sqlite';

import { DATABASE_NAME, runMigrations } from '@/src/db/migrations';
import { bootstrapAppPackageRegistry } from '@/src/db/app-package-registry';
import { seedDatabase } from '@/src/db/seed';
import { AppRuntimeProvider } from '@/src/domain/runtime-context';
import type { AppPackage } from '@/packages/shared/contracts/package';

export type LifeOSDatabase = SQLiteDatabase | null;

const DatabaseContext = createContext<LifeOSDatabase>(null);

export function useLifeOSDatabase(): LifeOSDatabase {
  return useContext(DatabaseContext);
}

export function LifeOSDatabaseProvider({ children, seedInDev = false }: { children: ReactNode; seedInDev?: boolean }) {
  const [db, setDb] = useState<LifeOSDatabase>(null);
  const [activePackage, setActivePackage] = useState<AppPackage | null>(null);

  useEffect(() => {
    let cancelled = false;
    let opened: SQLiteDatabase | null = null;

    const boot = async () => {
      try {
        opened = await openDatabaseAsync(DATABASE_NAME);
        await runMigrations(opened);
        const bootstrappedPackage = await bootstrapAppPackageRegistry(opened);
        await seedDatabase(opened, { seedInDev });
        if (!cancelled) {
          setActivePackage(bootstrappedPackage);
          setDb(opened);
        }
      } catch {
        if (!cancelled) {
          setActivePackage(null);
          setDb(null);
        }
      }
    };

    void boot();
    return () => {
      cancelled = true;
      void opened?.closeAsync?.();
    };
  }, [seedInDev]);

  return (
    <DatabaseContext.Provider value={db}>
      {db ? (
        <AppRuntimeProvider db={db} initialPackage={activePackage}>
          {children}
        </AppRuntimeProvider>
      ) : null}
    </DatabaseContext.Provider>
  );
}
