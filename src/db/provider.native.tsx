import { PropsWithChildren } from 'react';
import { SQLiteProvider, useSQLiteContext } from 'expo-sqlite';

import { DATABASE_NAME, runMigrations } from '@/src/db/migrations';
import { bootstrapAppPackageRegistry } from '@/src/db/app-package-registry';
import { seedDatabase } from '@/src/db/seed';

export type LifeOSDatabase = ReturnType<typeof useSQLiteContext>;

export function useLifeOSDatabase() {
  return useSQLiteContext();
}

export function LifeOSDatabaseProvider({ children, seedInDev = false }: PropsWithChildren<{ seedInDev?: boolean }>) {
  return (
    <SQLiteProvider
      databaseName={DATABASE_NAME}
      onInit={async (db) => {
        await runMigrations(db);
        await bootstrapAppPackageRegistry(db);
        await seedDatabase(db, { seedInDev });
      }}
    >
      {children}
    </SQLiteProvider>
  );
}
