import { ReactNode, createContext, useContext, useEffect, useState } from 'react';
import { SQLiteDatabase, openDatabaseAsync } from 'expo-sqlite';

import { DATABASE_NAME, runMigrations } from '@/src/db/migrations';
import { seedDatabase } from '@/src/db/seed';

export type LifeOSDatabase = SQLiteDatabase | null;

const DatabaseContext = createContext<LifeOSDatabase>(null);

export function useLifeOSDatabase(): LifeOSDatabase {
  return useContext(DatabaseContext);
}

export function LifeOSDatabaseProvider({ children, seedInDev = true }: { children: ReactNode; seedInDev?: boolean }) {
  const [db, setDb] = useState<LifeOSDatabase>(null);

  useEffect(() => {
    let cancelled = false;
    let opened: SQLiteDatabase | null = null;

    const boot = async () => {
      try {
        opened = await openDatabaseAsync(DATABASE_NAME);
        await runMigrations(opened);
        await seedDatabase(opened, { seedInDev });
        if (!cancelled) setDb(opened);
      } catch {
        if (!cancelled) setDb(null);
      }
    };

    void boot();
    return () => {
      cancelled = true;
      void opened?.closeAsync?.();
    };
  }, [seedInDev]);

  return <DatabaseContext.Provider value={db}>{children}</DatabaseContext.Provider>;
}
