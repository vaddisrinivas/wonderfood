import { SQLiteDatabase } from 'expo-sqlite';

import { loadCatalog } from '@/src/domain/catalog';
import { upsertRecord } from '@/src/db/records';
import { foodRecords } from '@/src/data/sample';

type SeedOptions = {
  seedInDev?: boolean;
};

export async function seedDatabase(db: SQLiteDatabase, options: SeedOptions = {}): Promise<void> {
  const catalog = loadCatalog();
  // A fresh release install must not render an empty shell. The bundled rows
  // are explicitly local sample data; live provider rows can replace them
  // after the user connects a source.
  if (!options.seedInDev) {
    return;
  }

  const existing = await db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM records');
  if (existing?.total && existing.total > 0) return;

  const createdAt = new Date().toISOString();

  for (const sample of foodRecords) {
    const manifest = catalog.activeManifest;
    await upsertRecord(
      db,
      manifest,
      {
        id: sample.id,
        title: sample.title,
        collection: sample.meta.includes('Recipe') || sample.meta.includes('Dining')
          ? 'recipe'
          : sample.meta.includes('Meal plan')
            ? 'meal_plan'
          : sample.meta.includes('Shopping')
            ? 'shopping_item'
            : 'inventory',
        properties: {
          status: sample.status,
          tone: sample.tone,
          meta: sample.meta,
          body: sample.body,
          source: sample.source,
        },
        source: {
          provider: 'sqlite',
          external_id: `sample-${sample.id}`,
          url: `wonderfood://sample/${sample.id}`,
          observed_at: createdAt,
          content_hash: null,
        },
        archived_at: null,
        created_at: createdAt,
        updated_at: createdAt,
      }
    );
  }

}
