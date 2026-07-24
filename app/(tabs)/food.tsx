import { Link, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, SectionTitle, VisualMark, sharedStyles } from '@/src/components/ui';
import { DashboardBlock, loadCatalog, setActiveDomainOverride, VisualToken } from '@/src/domain/catalog';
import { getDomainRecordCanonical, getSurfaceCollectionsForLabel, queryDomainCollections } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { buildSurfaceCatalog } from '@/src/domain/surface';
import { mergeVisualIdentity, visualAccent, visualGlyph, VisualAccent } from '@/src/domain/visual-identity';
import { useLifeOSDatabase } from '@/src/db/provider';
import { archiveRecord, restoreRecord, upsertRecord } from '@/src/db/records';
import { seedDatabase } from '@/src/db/seed';
import { exportRecoverySnapshot, type RecoveryExport } from '@/src/db/migrations';
import { importRecoverySnapshot } from '@/src/db/recovery';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';

type FoodRecordView = DomainRecordViewModel;
type FoodMode = 'Today' | 'Kitchen' | 'Plan' | 'Recipes' | 'Shop';
type FoodTone = 'moss' | 'amber' | 'blue' | 'plum' | 'red';

const FOOD_MODES: Array<{ id: FoodMode; label: string; glyph: string }> = [
  { id: 'Today', label: 'Today', glyph: '🍽️' },
  { id: 'Kitchen', label: 'Kitchen', glyph: '🥬' },
  { id: 'Plan', label: 'Plan', glyph: '🗓️' },
  { id: 'Recipes', label: 'Recipes', glyph: '🍳' },
  { id: 'Shop', label: 'Shop', glyph: '🧺' },
];

const fallbackMeals = [
  { title: 'Breakfast: Greek yogurt bowl', detail: 'Blueberries, walnuts, honey. Protein estimate pending review.', badge: '+22g protein', tone: 'moss' as const },
  { title: 'Lunch: Chickpea spinach wraps', detail: 'Use spinach before Sunday; add tomatoes from shopping list.', badge: 'use first', tone: 'amber' as const },
  { title: 'Dinner: Salmon rice bowls', detail: 'Receipt draft matched salmon, cucumber, rice vinegar.', badge: 'draft', tone: 'red' as const },
];

const fallbackKitchen = [
  { title: 'Baby spinach', detail: 'Fridge lot A3. Expires Sun. Great for wraps or eggs.', badge: '2 days', tone: 'amber' as const },
  { title: 'Salmon fillets', detail: 'Freezer. Receipt confidence 92%. 1.4 lb estimated.', badge: 'new', tone: 'moss' as const },
  { title: 'Greek yogurt', detail: 'Opened yesterday. Protein value reviewed by user.', badge: 'reviewed', tone: 'moss' as const },
  { title: 'Chickpeas', detail: 'Pantry. 3 cans. Recipe match: spinach wraps.', badge: '3 cans', tone: 'blue' as const },
  { title: 'Blueberries', detail: 'Fridge. Price captured from receipt draft.', badge: '$3.99', tone: 'red' as const },
];

const fallbackShop = [
  { title: 'Add to inventory', detail: 'Salmon fillets, baby spinach, blueberries', badge: '3 items', tone: 'moss' as const },
  { title: 'Keep on shopping list', detail: 'Rice vinegar, tortillas, lemons, oats', badge: '4 left', tone: 'amber' as const },
  { title: 'Ignore household line', detail: 'Dish soap stays out of food inventory.', badge: 'ignored', tone: 'blue' as const },
];

const viewCopy: Record<string, { title: string; subtitle: string; empty: string }> = {
  Overview: {
    title: 'Food home',
    subtitle: 'Dinner, pantry, shopping and cooking memory.',
    empty: 'Capture a meal, pantry item or receipt to wake up this food space.',
  },
  Meals: {
    title: 'Meals',
    subtitle: 'Plans, actuals and recipe decisions for the week.',
    empty: 'Plan dinner or ask AI to draft meals from the kitchen.',
  },
  Kitchen: {
    title: 'Kitchen',
    subtitle: 'What you have, what expires, what needs correction.',
    empty: 'Add pantry items to build the kitchen.',
  },
  Shopping: {
    title: 'Shopping',
    subtitle: 'To buy, receipt review and put-away flow.',
    empty: 'Add missing ingredients or generate a list from meal plans.',
  },
};

function copyForView(domainLabel: string, view: string) {
  if (domainLabel === 'Food') {
    return viewCopy[view] ?? viewCopy.Overview;
  }
  return {
    title: view === 'Overview' ? `${domainLabel} command center` : view,
    subtitle: `${domainLabel} views, records and actions.`,
    empty: `Add a ${domainLabel.toLowerCase()} item to wake up this space.`,
  };
}

function pickByNeed(records: FoodRecordView[], needle: string, fallbackIndex: number) {
  return records.find((item) => `${item.collection} ${item.meta} ${item.title}`.toLowerCase().includes(needle)) ?? records[fallbackIndex];
}

function pickByCollections(records: FoodRecordView[], collections: string[], fallbackIndex: number) {
  const wanted = new Set(collections);
  return records.find((item) => wanted.has(item.collection)) ?? records[fallbackIndex];
}

const foodPriority: Record<string, number> = {
  meal_plan: 100,
  recipe: 94,
  inventory: 84,
  shopping_item: 76,
  shopping_demand: 72,
  inventory_lot: 68,
  meal_log: 56,
  purchase: 48,
  product: 38,
  nutrition_profile: 30,
  recipe_step: 28,
  purchase_line: 24,
  inventory_movement: 18,
  meal_consumption: 12,
  source_record: 4,
};

function productRank(records: FoodRecordView[]) {
  return [...records].sort((left, right) => (foodPriority[right.collection] ?? 40) - (foodPriority[left.collection] ?? 40));
}

function countSetting(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const FOOD_SECTIONS = ['hero', 'tabs', 'manifest', 'collections', 'widgets', 'workspace', 'attention', 'view', 'package'] as const;
type FoodSection = typeof FOOD_SECTIONS[number];
type FoodWidget = { title: string; detail: string; tone: 'moss' | 'blue' | 'amber' | 'plum' | 'neutral'; href: string };
const OPERATING_VIEW_SECTIONS = ['assemblyTable', 'weekPlan', 'pantryTimeline', 'shoppingChecklist'] as const;
type OperatingViewSection = typeof OPERATING_VIEW_SECTIONS[number];

function orderedFoodSections(value: string) {
  const allowed = new Set<string>(FOOD_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is FoodSection => allowed.has(section));
  const missing = FOOD_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

function orderedOperatingViews(value: string) {
  const allowed = new Set<string>(OPERATING_VIEW_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is OperatingViewSection => allowed.has(section));
  const missing = OPERATING_VIEW_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

function toWidgetTone(value: string): FoodWidget['tone'] {
  return value === 'moss' || value === 'blue' || value === 'amber' || value === 'plum' || value === 'neutral' ? value : 'neutral';
}

function softForAccent(accent: VisualAccent, themed = colors) {
  if (accent === 'moss') return themed.mossSoft;
  if (accent === 'amber') return themed.amberSoft;
  if (accent === 'blue') return themed.blueSoft;
  if (accent === 'plum') return themed.plumSoft;
  return themed.canvas;
}

function inkForAccent(accent: VisualAccent, themed = colors) {
  if (accent === 'moss') return themed.moss;
  if (accent === 'amber') return themed.amber;
  if (accent === 'blue') return themed.blue;
  if (accent === 'plum') return themed.plum;
  return themed.ink;
}

function parseFoodWidgets(value: string): FoodWidget[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [title, detail, tone = 'neutral', href = '/config'] = line.split('|').map((part) => part.trim());
      return {
        title: title || 'Profile widget',
        detail: detail || 'Configured from the portable LifeOS profile.',
        tone: toWidgetTone(tone),
        href: href.startsWith('/') ? href : '/config',
      };
    });
}

function parseDashboardBlockOverrides(value: string, domainId: string): DashboardBlock[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [
        id,
        title,
        subtitle,
        kind = 'list',
        tone = 'neutral',
        collections = '',
        match = '',
        limit = '3',
        href = '/config',
        size = 'standard',
      ] = line.split('|').map((part) => part.trim());
      const parsedLimit = Number.parseInt(limit, 10);
      return {
        id: id || `${domainId}:custom-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`,
        surface: `${domainId}.overview`,
        title: title || 'Custom dashboard block',
        subtitle: subtitle || 'Configured from the app profile.',
        kind: kind === 'spotlight' || kind === 'metric' || kind === 'action' ? kind : 'list',
        tone: tone === 'moss' || tone === 'amber' || tone === 'plum' || tone === 'blue' ? tone : 'neutral',
        size: size === 'compact' || size === 'wide' || size === 'feature' ? size : 'standard',
        query: {
          collections: collections ? collections.split(',').map((item) => item.trim()).filter(Boolean) : undefined,
          match: match || undefined,
          limit: Number.isFinite(parsedLimit) ? Math.max(0, Math.min(20, parsedLimit)) : 3,
        },
        href: href.startsWith('/') ? href : '/config',
      } satisfies DashboardBlock;
    });
}

function recordsForBlock(block: DashboardBlock, records: FoodRecordView[]) {
  const collections = new Set(block.query.collections ?? []);
  const matcher = block.query.match ? new RegExp(block.query.match, 'i') : null;
  const limit = block.query.limit ?? 3;
  return records
    .filter((record) => {
      const collectionMatch = collections.size === 0 || collections.has(record.collection);
      const textMatch = !matcher || matcher.test(`${record.collection} ${record.status} ${record.meta} ${record.title} ${record.body}`);
      return collectionMatch && textMatch;
    })
    .slice(0, limit);
}

function humanizeCollection(collection: string) {
  return collection
    .split('_')
    .map((part) => part ? `${part[0].toUpperCase()}${part.slice(1)}` : part)
    .join(' ');
}

function slugId(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'item';
}

export default function FoodScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 32, 280) : Math.min(Math.max(width - 96, 900), 1280);
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeDomainId, activeManifest } = loadCatalog();
  const visualIdentity = useMemo(() => mergeVisualIdentity(activeManifest, settings.runtime.visualIdentityOverrides), [activeManifest, settings.runtime.visualIdentityOverrides]);
  const domainLabel = activeManifest.label;
  const isFoodDomain = activeDomainId === 'food';
  const theme = useLifeOSTheme();
  const surfaceCatalog = useMemo(() => buildSurfaceCatalog(activeManifest), [activeManifest]);
  const views = surfaceCatalog.tabs;
  const defaultTab = views[0] ?? 'Overview';
  const db = useLifeOSDatabase();
  const [active, setActive] = useState(defaultTab);
  const [foodMode, setFoodMode] = useState<FoodMode>('Today');
  const [records, setRecords] = useState<FoodRecordView[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshNonce, setRefreshNonce] = useState(0);
  const [notice, setNotice] = useState('');
  const [lastArchivedId, setLastArchivedId] = useState<string | null>(null);
  const [backupSnapshot, setBackupSnapshot] = useState<RecoveryExport | null>(null);

  useEffect(() => {
    let cancelled = false;
    const collections = getSurfaceCollectionsForLabel(active);
    const load = async () => {
      setLoading(true);
      const next = await queryDomainCollections(db, collections);
      if (!cancelled) {
        setRecords(next as FoodRecordView[]);
        setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [active, activeDomainId, db, refreshNonce]);

  const shown = useMemo(() => {
    if (active === 'Overview') return records;
    return records.filter((item) =>
      item.collection.toLowerCase().includes(active.toLowerCase())
      || item.meta.toLowerCase().includes(active.toLowerCase())
      || item.title.toLowerCase().includes(active.toLowerCase())
    );
  }, [active, records]);

  const rankedRecords = productRank(records);
  const todayMeal = pickByCollections(rankedRecords, ['meal_plan', 'recipe'], 0) ?? pickByNeed(rankedRecords, 'meal', 0);
  const kitchenItem = pickByCollections(rankedRecords, ['inventory', 'inventory_lot'], 1) ?? pickByNeed(rankedRecords, 'pantry', 1);
  const shoppingItem = pickByCollections(rankedRecords, ['shopping_item', 'shopping_demand'], 2) ?? pickByNeed(rankedRecords, 'shopping', 2);
  const activeCopy = copyForView(domainLabel, active);
  const foodConfig = settings.runtime.surfaceConfig.food;
  const columnLimit = countSetting(foodConfig.columnLimit, 4);
  const attentionLimit = countSetting(foodConfig.attentionLimit, 3);
  const mealRecords = rankedRecords.filter((item) => ['meal_plan', 'meal_log', 'recipe'].includes(item.collection)).slice(0, columnLimit);
  const kitchenRecords = rankedRecords.filter((item) => ['inventory', 'inventory_lot', 'product', 'inventory_movement', 'purchase_line'].includes(item.collection)).slice(0, columnLimit);
  const shoppingRecords = rankedRecords.filter((item) => ['shopping_item', 'shopping_demand', 'purchase', 'purchase_line'].includes(item.collection)).slice(0, columnLimit);
  const surfaceColumns = activeManifest.surfaces.slice(0, 3).map((surface) => ({
    id: surface.id,
    title: surface.label,
    subtitle: surface.views?.slice(0, 3).join(' · ') || surface.collections.join(' · ') || 'Workspace view',
    records: records.filter((record) => surface.collections.includes(record.collection)).slice(0, columnLimit),
    empty: `No ${surface.label.toLowerCase()} records yet.`,
  }));
  const reviewRows = rankedRecords.filter((item) => /use|planned|buy|tonight|open|review/i.test(`${item.status} ${item.meta}`)).slice(0, attentionLimit);
  const foodSections = orderedFoodSections(foodConfig.sectionOrder);
  const widgets = parseFoodWidgets(foodConfig.widgets);
  const configuredBlocks = parseDashboardBlockOverrides(foodConfig.dashboardBlocks, activeDomainId);
  const manifestBlocks = (configuredBlocks.length ? configuredBlocks : (activeManifest.dashboard_blocks ?? []))
    .filter((block) => block.surface.startsWith(`${activeDomainId}.`));
  const collectionCounts = records.reduce<Record<string, number>>((counts, record) => {
    counts[record.collection] = (counts[record.collection] ?? 0) + 1;
    return counts;
  }, {});
  const unplacedCollections = activeManifest.collections.filter((collection) =>
    !activeManifest.surfaces.some((surface) => surface.collections.includes(collection))
  );

  const toggleShoppingRecord = async (record: FoodRecordView) => {
    const nextStatus = /in cart|bought/i.test(record.status) ? 'To buy' : 'In cart';
    const nextTone: FoodRecordView['tone'] = nextStatus === 'In cart' ? 'moss' : 'blue';
    setRecords((current) => current.map((item) => item.id === record.id ? { ...item, status: nextStatus, tone: nextTone } : item));
    if (!db) {
      return;
    }
    const canonical = await getDomainRecordCanonical(db, record.id);
    if (!canonical) {
      return;
    }
    await upsertRecord(db, activeManifest, {
      ...canonical,
      properties: {
        ...canonical.properties,
        status: nextStatus,
        tone: nextTone,
      },
      updated_at: new Date().toISOString(),
    });
    setNotice(`${record.title} is now ${nextStatus.toLowerCase()}.`);
    setRefreshNonce((value) => value + 1);
  };

  const loadDemoHousehold = async () => {
    if (!db) {
      setNotice('Storage is still starting. Try again in a moment.');
      return;
    }
    await seedDatabase(db, { seedInDev: true });
    setNotice('Demo household loaded. You can now plan, shop, edit, archive and undo.');
    setRefreshNonce((value) => value + 1);
  };

  const quickAddFoodRecord = async (collection: string, title: string, properties: Record<string, unknown>) => {
    if (!db) {
      setNotice('Storage is still starting. Try again in a moment.');
      return;
    }
    const now = new Date().toISOString();
    const id = `food-${collection}-${Date.now().toString(36)}`;
    await upsertRecord(db, activeManifest, {
      id,
      title,
      collection,
      properties: {
        status: 'Active',
        tone: 'moss',
        meta: 'Added in WonderFood',
        body: '',
        source: 'user · local',
        ...properties,
      },
      source: {
        provider: 'user',
        external_id: id,
        url: null,
        observed_at: now,
        content_hash: null,
      },
      archived_at: null,
      created_at: now,
      updated_at: now,
      relations: [],
    });
    setNotice(`Added ${title}.`);
    setRefreshNonce((value) => value + 1);
  };

  const archiveFirstVisibleRecord = async () => {
    if (!db) {
      setNotice('Storage is still starting. Try again in a moment.');
      return;
    }
    const target = rankedRecords[0];
    if (!target) {
      setNotice('Nothing to archive yet.');
      return;
    }
    await archiveRecord(db, target.id);
    setLastArchivedId(target.id);
    setNotice(`Archived ${target.title}. Undo is available.`);
    setRefreshNonce((value) => value + 1);
  };

  const undoLastArchive = async () => {
    if (!db || !lastArchivedId) {
      setNotice('No archive to undo yet.');
      return;
    }
    await restoreRecord(db, lastArchivedId);
    setNotice('Undo complete. Record restored.');
    setLastArchivedId(null);
    setRefreshNonce((value) => value + 1);
  };

  const exportBackup = async () => {
    if (!db) {
      setNotice('Storage is still starting. Try again in a moment.');
      return;
    }
    const snapshot = await exportRecoverySnapshot(db);
    setBackupSnapshot(snapshot);
    const totalRows = snapshot.tables.reduce((sum, table) => sum + table.rows.length, 0);
    setNotice(`Kitchen backup saved: ${totalRows} items.`);
  };

  const restoreBackup = async () => {
    if (!db || !backupSnapshot) {
      setNotice('Export a backup first.');
      return;
    }
    await importRecoverySnapshot(db, backupSnapshot);
    setNotice('Kitchen backup restored.');
    setRefreshNonce((value) => value + 1);
  };

  const renderFoodSection = (section: FoodSection) => {
    switch (section) {
      case 'hero':
        return foodConfig.showHero ? (
          <View key={section} style={[styles.dashboard, compact && styles.dashboardCompact]}>
            <Card tone="moss" style={styles.hero}>
              <View style={styles.heroHeader}>
                <Pill tone="moss">{isFoodDomain ? 'TONIGHT' : `${domainLabel.toUpperCase()} ACTIVE`}</Pill>
                <Text style={[styles.heroMeta, { color: theme.colors.muted }]}>{loading ? `Loading ${domainLabel.toLowerCase()}...` : `${records.length} ${domainLabel.toLowerCase()} records`}</Text>
              </View>
              <Text style={[styles.heroTitle, { color: theme.colors.ink }, compact && styles.heroTitleCompact]}>
                {isFoodDomain && todayMeal ? `Tonight: ${todayMeal.title}` : todayMeal?.title ?? `Run your ${domainLabel.toLowerCase()} workspace.`}
              </Text>
              <Text style={[styles.heroBody, { color: theme.colors.ink }, compact && styles.heroBodyCompact]}>
                {todayMeal?.body || todayMeal?.meta || `Open a view, ask with context, or add the next ${domainLabel.toLowerCase()} item.`}
              </Text>
              <View style={styles.heroActions}>
                <ActionButton label={todayMeal ? `Open ${isFoodDomain ? 'dinner' : 'record'}` : `Ask ${domainLabel} AI`} onPress={() => router.push(todayMeal ? `/record/${todayMeal.id}` : '/chat')} />
                <ActionButton label={`Ask ${domainLabel} AI`} quiet onPress={() => router.push('/chat')} />
              </View>
              <View style={styles.commandLane}>
                {isFoodDomain ? (
                  <>
                    <CommandStep index="01" title="Dinner" detail={todayMeal?.title ?? 'Pick tonight'} tone="moss" href={todayMeal ? `/record/${todayMeal.id}` : '/chat'} />
                    <CommandStep index="02" title="Pantry risk" detail={kitchenItem?.title ?? 'Nothing urgent'} tone="amber" href={kitchenItem ? `/record/${kitchenItem.id}` : '/capture'} />
                    <CommandStep index="03" title="Shopping gap" detail={shoppingItem?.title ?? 'No blockers'} tone="blue" href={shoppingItem ? `/record/${shoppingItem.id}` : '/capture'} />
                    <CommandStep index="04" title="AI prep" detail="Ask, cook, log, update" tone="plum" href="/chat" />
                  </>
                ) : surfaceColumns.map((surface, index) => (
                  <CommandStep key={surface.id} index={String(index + 1).padStart(2, '0')} title={surface.title} detail={surface.subtitle} tone={index === 0 ? 'moss' : index === 1 ? 'blue' : 'plum'} href="/config" />
                ))}
              </View>
            </Card>

            <View style={[styles.todayRail, compact && styles.todayRailCompact]}>
              <FeatureCard tone="amber" label={isFoodDomain ? 'Use soon' : 'First surface'} item={isFoodDomain ? kitchenItem : surfaceColumns[0]?.records[0]} fallbackTitle={isFoodDomain ? 'Nothing urgent' : surfaceColumns[0]?.title ?? 'No records yet'} fallbackBody={isFoodDomain ? 'Use-soon pantry items will appear here.' : surfaceColumns[0]?.subtitle ?? 'Connect a source or capture a record.'} />
              <FeatureCard tone="blue" label={isFoodDomain ? 'Shopping' : 'Sources'} item={isFoodDomain ? shoppingItem : undefined} fallbackTitle={isFoodDomain ? 'No shopping pressure' : `${domainLabel} sources`} fallbackBody={isFoodDomain ? 'Missing ingredients and receipt items will appear here.' : 'Notion, Sheets and device records can feed this space.'} />
            </View>
          </View>
        ) : null;
      case 'tabs':
        return foodConfig.showViewTabs ? (
          <ScrollView key={section} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.segments, compact && styles.segmentsCompact, { backgroundColor: theme.colors.canvas }]}>
            {views.map((view) => (
              <Pressable
                key={view}
                accessibilityRole="button"
                accessibilityState={{ selected: active === view }}
                onPress={() => setActive(view)}
                style={[styles.segment, compact && styles.segmentCompact, active === view && styles.segmentActive, active === view && { backgroundColor: theme.colors.paper }]}
              >
                <Text style={[styles.segmentText, { color: active === view ? theme.colors.ink : theme.colors.muted }]}>{view}</Text>
              </Pressable>
            ))}
          </ScrollView>
        ) : null;
      case 'widgets':
        return foodConfig.showWidgets && widgets.length ? (
          <View key={section}>
            <SectionTitle title="Shortcuts" />
            <View style={[styles.widgetGrid, compact && styles.boardCompact]}>
              {widgets.map((widget) => (
                <Pressable key={`${widget.title}-${widget.href}`} accessibilityRole="button" onPress={() => router.push(widget.href as never)} style={({ pressed }) => [styles.widgetPress, pressed && styles.pressed]}>
                  <Card tone={widget.tone === 'neutral' ? undefined : widget.tone} style={styles.widgetCard}>
                    <Text style={[styles.widgetTitle, { color: theme.colors.ink }]}>{widget.title}</Text>
                    <Text style={[styles.widgetDetail, { color: theme.colors.muted }]}>{widget.detail}</Text>
                    <Text style={[styles.widgetRoute, { color: theme.colors.moss }]}>Open →</Text>
                  </Card>
                </Pressable>
              ))}
            </View>
          </View>
        ) : null;
      case 'manifest':
        return active === 'Overview' && foodConfig.showManifestBlocks && manifestBlocks.length ? (
          <View key={section}>
            <SectionTitle title={`${domainLabel} dashboard`} action="Tune layout" href="/config" />
            <View style={[styles.manifestGrid, compact && styles.boardCompact]}>
              {manifestBlocks.map((block) => (
                <ManifestDashboardBlock
                  key={block.id}
                  block={block}
                  records={recordsForBlock(block, records)}
                />
              ))}
            </View>
          </View>
        ) : null;
      case 'collections':
        return active === 'Overview' && foodConfig.showCollectionAtlas ? (
          <View key={section}>
            <SectionTitle title={`${domainLabel} map`} action="Settings" href="/config" />
            <Card style={styles.atlasHero}>
              <View style={styles.atlasHeroCopy}>
                <Text style={[styles.atlasKicker, { color: theme.colors.moss }]}>KITCHEN MEMORY</Text>
                <Text style={[styles.atlasTitle, { color: theme.colors.ink }]}>{activeManifest.collections.length} food areas ready.</Text>
                <Text style={[styles.atlasBody, { color: theme.colors.muted }]}>Meals, pantry, recipes, shopping, purchases and nutrition stay organized without making you manage the machinery.</Text>
              </View>
              <View style={styles.atlasStats}>
                <CollectionStat label="Records loaded" value={String(records.length)} tone="moss" />
                <CollectionStat label="Views" value={activeManifest.surfaces.map((surface) => surface.label).join(' · ')} tone="blue" />
              </View>
            </Card>
            <View style={[styles.atlasGrid, compact && styles.boardCompact]}>
              {activeManifest.surfaces.map((surface, index) => (
                <CollectionGroup
                  key={surface.id}
                  title={surface.label}
                  subtitle={surface.views?.slice(0, 4).join(' · ') || 'Workspace view'}
                  collections={surface.collections}
                  counts={collectionCounts}
                  visuals={visualIdentity.collections ?? {}}
                  tone={index === 0 ? 'moss' : index === 1 ? 'amber' : 'blue'}
                />
              ))}
              {unplacedCollections.length ? (
                <CollectionGroup
                  title="More food context"
                  subtitle="Extra details used when you ask for help"
                  collections={unplacedCollections}
                  counts={collectionCounts}
                  visuals={visualIdentity.collections ?? {}}
                  tone="plum"
                />
              ) : null}
            </View>
          </View>
        ) : null;
      case 'workspace':
        return active === 'Overview' && foodConfig.showWorkspace ? (
          <View key={section}>
              <SectionTitle title={`${domainLabel} operating board`} action="Ask Food AI" href="/chat" />
            <View style={[styles.board, compact && styles.boardCompact]}>
              {isFoodDomain ? (
                <>
                  <RecordColumn title="Meals" subtitle="Tonight and next plans" records={mealRecords} visuals={visualIdentity.collections ?? {}} empty="Plan dinner from pantry." />
                <RecordColumn title="Kitchen" subtitle="Use-soon and available" records={kitchenRecords} visuals={visualIdentity.collections ?? {}} empty="Add pantry items." />
                  <RecordColumn title="Shopping" subtitle="Missing and to-buy" records={shoppingRecords} visuals={visualIdentity.collections ?? {}} empty="No shopping pressure." />
                </>
              ) : surfaceColumns.map((surface) => (
                <RecordColumn key={surface.id} title={surface.title} subtitle={surface.subtitle} records={surface.records} visuals={visualIdentity.collections ?? {}} empty={surface.empty} />
              ))}
            </View>
            {isFoodDomain && foodConfig.showOperatingViews ? (
              <FoodOperatingViews
                meals={mealRecords}
                kitchen={kitchenRecords}
                shopping={shoppingRecords}
                compact={compact}
                order={orderedOperatingViews(foodConfig.operatingViewOrder)}
                onToggleShopping={toggleShoppingRecord}
              />
            ) : null}
          </View>
        ) : null;
      case 'attention':
        return active === 'Overview' && foodConfig.showAttention ? (
          <View key={section}>
            <SectionTitle title="Needs your call" />
            <View style={[styles.attentionGrid, compact && styles.boardCompact]}>
              {reviewRows.length ? reviewRows.map((row) => (
                <MiniRecord key={row.id} record={row} />
              )) : (
                <Card tone="moss" style={styles.emptyCard}>
                  <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>Nothing needs review</Text>
                  <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>Suggestions and possible changes wait here until you say yes.</Text>
                </Card>
              )}
            </View>
          </View>
        ) : null;
      case 'view':
        return active !== 'Overview' ? (
          <View key={section}>
            <SectionTitle title={activeCopy.title} action="Ask" href="/chat" />
            <Text style={[styles.viewSubtitle, { color: theme.colors.muted }]}>{activeCopy.subtitle}</Text>
            {loading ? <Text style={[styles.loading, { color: theme.colors.muted }]}>{`Loading ${domainLabel.toLowerCase()} records...`}</Text> : null}

            <View style={[styles.records, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
              {shown.length ? shown.map((record) => (
                <RecordListItem key={record.id} record={record} visual={visualIdentity.collections?.[record.collection]} />
              )) : (
                <Card tone="moss" style={styles.emptyCard}>
                  <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>{activeCopy.empty}</Text>
                  <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>{`Add food, ask ${domainLabel} AI, or load a sample kitchen.`}</Text>
                  <Link href="/capture" style={[styles.cardLink, { color: theme.colors.moss }]}>{`Capture ${domainLabel.toLowerCase()} →`}</Link>
                </Card>
              )}
            </View>
          </View>
        ) : null;
      case 'package':
        return foodConfig.showPackageCard ? (
          <Card key={section} style={styles.configCard}>
            <View style={styles.configCopy}>
              <Text style={[styles.configTitle, { color: theme.colors.ink }]}>Want to change the kitchen?</Text>
              <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>Ask AI to reshape the screen, or open Settings for advanced controls.</Text>
            </View>
            <Link href="/config" style={[styles.configLink, { color: theme.colors.moss }]}>Settings</Link>
          </Card>
        ) : null;
      default:
        return null;
    }
  };

  if (isFoodDomain) {
    return (
      <FoodDemoSurface
        mode={foodMode}
        onModeChange={setFoodMode}
        records={rankedRecords}
        meals={mealRecords}
        kitchen={kitchenRecords}
        shopping={shoppingRecords}
        reviewRows={reviewRows}
        loading={loading}
        compact={compact}
        contentWidth={Math.min(contentWidth, 760)}
        onToggleShopping={toggleShoppingRecord}
        onAsk={() => router.push('/chat')}
        onLoadDemo={loadDemoHousehold}
        onQuickAdd={quickAddFoodRecord}
        onArchiveFirst={archiveFirstVisibleRecord}
        onUndoArchive={undoLastArchive}
        onExportBackup={exportBackup}
        onRestoreBackup={restoreBackup}
        canUndoArchive={Boolean(lastArchivedId)}
        canRestoreBackup={Boolean(backupSnapshot)}
        notice={notice}
      />
    );
  }

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={[styles.content, { width: contentWidth }]}>
          <View style={styles.topbar}>
            <View>
              <Text style={[styles.brand, { color: theme.colors.moss }]}>{`LIFEOS / ${domainLabel.toUpperCase()}`}</Text>
              <Text style={[styles.date, { color: theme.colors.muted }]}>{activeManifest.surfaces.map((surface) => surface.label).join(', ')}</Text>
            </View>
            <View style={styles.topActions}>
              <Link href="/search" style={[styles.topIcon, { color: theme.colors.ink }]}>⌕</Link>
              <Link href="/capture" style={[styles.capture, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>＋ Add</Link>
              <Link href="/settings" style={[styles.avatar, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>SV</Link>
            </View>
          </View>

          {foodSections.map(renderFoodSection)}
        </View>
      </ScrollView>
    </Page>
  );
}

function ManifestDashboardBlock({ block, records }: { block: DashboardBlock; records: FoodRecordView[] }) {
  const theme = useLifeOSTheme();
  const primary = records[0];
  const href = primary ? { pathname: '/record/[id]', params: { id: primary.id } } : block.href;
  const size = block.size ?? 'standard';
  const metricValue = block.kind === 'metric'
    ? records.length
      ? records.map((record) => record.title).slice(0, 2).join(' + ')
      : 'No records yet'
    : primary?.title ?? (block.kind === 'action' ? 'Open workspace' : 'Awaiting data');
  return (
    <Link href={href as never} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [
        styles.manifestBlockPress,
        size === 'compact' && styles.manifestBlockPressCompact,
        size === 'wide' && styles.manifestBlockPressWide,
        size === 'feature' && styles.manifestBlockPressFeature,
        pressed && styles.pressed,
      ]}>
        <Card tone={block.tone === 'neutral' ? undefined : block.tone} style={[
          styles.manifestBlock,
          size === 'compact' && styles.manifestBlockCompact,
          size === 'wide' && styles.manifestBlockWide,
          size === 'feature' && styles.manifestBlockFeature,
        ]}>
          <View style={styles.manifestBlockTop}>
            <Text style={[styles.manifestBlockKind, { color: theme.colors.muted }]}>{block.kind.toUpperCase()}</Text>
            <Pill tone={block.tone}>{records.length ? `${records.length} hit${records.length === 1 ? '' : 's'}` : 'config'}</Pill>
          </View>
          <Text style={[styles.manifestBlockTitle, { color: theme.colors.ink }]} numberOfLines={size === 'compact' ? 1 : 2}>{block.title}</Text>
          <Text style={[styles.manifestBlockSubtitle, { color: theme.colors.muted }]} numberOfLines={size === 'compact' ? 2 : 3}>{block.subtitle}</Text>
          <Text style={[styles.manifestBlockValue, { color: theme.colors.ink }]} numberOfLines={block.kind === 'list' ? 1 : 2}>{metricValue}</Text>
          {block.kind === 'list' ? (
            <View style={styles.manifestBlockList}>
              {(records.length ? records : []).slice(0, 3).map((record) => (
                <View key={record.id} style={[styles.manifestBlockRow, { borderTopColor: theme.colors.line }]}>
                  <Text style={[styles.manifestBlockDot, { color: theme.colors.moss }]}>•</Text>
                  <Text style={[styles.manifestBlockRowText, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
                </View>
              ))}
              {!records.length ? <Text style={[styles.manifestBlockEmpty, { color: theme.colors.muted }]}>Add matching records or edit the block query.</Text> : null}
            </View>
          ) : null}
          <Text style={[styles.manifestBlockRoute, { color: theme.colors.moss }]}>Open →</Text>
        </Card>
      </Pressable>
    </Link>
  );
}

function FoodDemoSurface({ mode, onModeChange, records, meals, kitchen, shopping, reviewRows, loading, compact, contentWidth, onToggleShopping, onAsk, onLoadDemo, onQuickAdd, onArchiveFirst, onUndoArchive, onExportBackup, onRestoreBackup, canUndoArchive, canRestoreBackup, notice }: {
  mode: FoodMode;
  onModeChange: (mode: FoodMode) => void;
  records: FoodRecordView[];
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  reviewRows: FoodRecordView[];
  loading: boolean;
  compact: boolean;
  contentWidth: number;
  onToggleShopping: (record: FoodRecordView) => void;
  onAsk: () => void;
  onLoadDemo: () => void;
  onQuickAdd: (collection: string, title: string, properties: Record<string, unknown>) => void;
  onArchiveFirst: () => void;
  onUndoArchive: () => void;
  onExportBackup: () => void;
  onRestoreBackup: () => void;
  canUndoArchive: boolean;
  canRestoreBackup: boolean;
  notice: string;
}) {
  const theme = useLifeOSTheme();
  const today = new Date();
  const title = mode === 'Today'
    ? 'WonderFood'
    : mode;
  const subtitle = {
    Today: today.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' }),
    Kitchen: 'Fresh, frozen, pantry, use-first',
    Plan: 'A calm week from what you already have',
    Recipes: 'Cook now, almost ready, worth shopping for',
    Shop: 'A list with reasons, not errands',
  }[mode];
  return (
    <Page>
      <View style={styles.foodDemoRoot} testID="food-screen" accessibilityLabel="Food screen">
        <ScrollView testID="food-golden-scroll" contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
          <View style={[styles.foodDemoContent, !compact && { width: contentWidth }, compact && styles.foodDemoContentCompact]}>
            <View style={styles.foodDemoTopbar}>
              <View style={styles.foodDemoHeadline}>
                <Text style={[styles.foodDemoTitle, { color: theme.colors.ink }]}>{title}</Text>
                <Text style={[styles.foodDemoSubtitle, { color: theme.colors.muted }]}>{loading ? 'Loading your kitchen…' : subtitle}</Text>
              </View>
              <View style={styles.foodDemoTopActions}>
                <Link href="/search" asChild>
                  <Pressable accessibilityRole="button" accessibilityLabel="Open food search" testID="food-open-search" style={({ pressed }) => [styles.foodDemoIconButton, pressed && styles.pressed]}>
                    <Text style={[styles.foodDemoIcon, { color: theme.colors.ink }]}>⌕</Text>
                  </Pressable>
                </Link>
                <Link href="/capture" asChild>
                  <Pressable accessibilityRole="button" accessibilityLabel="Open food capture" testID="food-open-capture" style={({ pressed }) => [styles.foodDemoIconButton, pressed && styles.pressed]}>
                    <Text style={[styles.foodDemoIcon, { color: theme.colors.ink }]}>＋</Text>
                  </Pressable>
                </Link>
                <Pressable accessibilityRole="button" accessibilityLabel="Ask Food AI" testID="food-ask-ai" onPress={onAsk} style={({ pressed }) => [styles.foodAskPill, compact && styles.foodAskPillCompact, { backgroundColor: theme.dark ? theme.colors.plumSoft : '#FFD5C8' }, pressed && styles.pressed]}>
                  <Text style={[styles.foodAskText, { color: theme.dark ? theme.colors.ink : '#4B241D' }]}>Ask</Text>
                </Pressable>
              </View>
            </View>
            <FoodHeroPlate records={records} meals={meals} kitchen={kitchen} shopping={shopping} onAsk={onAsk} onLoadDemo={onLoadDemo} />
            <FoodDemoBody
              mode={mode}
              records={records}
              meals={meals}
              kitchen={kitchen}
              shopping={shopping}
              reviewRows={reviewRows}
              onToggleShopping={onToggleShopping}
              onLoadDemo={onLoadDemo}
              onQuickAdd={onQuickAdd}
              onArchiveFirst={onArchiveFirst}
              onUndoArchive={onUndoArchive}
              onExportBackup={onExportBackup}
              onRestoreBackup={onRestoreBackup}
              canUndoArchive={canUndoArchive}
              canRestoreBackup={canRestoreBackup}
              notice={notice}
            />
          </View>
        </ScrollView>
        <FoodDemoNav active={mode} onChange={onModeChange} />
      </View>
    </Page>
  );
}

function FoodDemoBody({ mode, records, meals, kitchen, shopping, reviewRows, onToggleShopping, onLoadDemo, onQuickAdd, onArchiveFirst, onUndoArchive, onExportBackup, onRestoreBackup, canUndoArchive, canRestoreBackup, notice }: {
  mode: FoodMode;
  records: FoodRecordView[];
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  reviewRows: FoodRecordView[];
  onToggleShopping: (record: FoodRecordView) => void;
  onLoadDemo: () => void;
  onQuickAdd: (collection: string, title: string, properties: Record<string, unknown>) => void;
  onArchiveFirst: () => void;
  onUndoArchive: () => void;
  onExportBackup: () => void;
  onRestoreBackup: () => void;
  canUndoArchive: boolean;
  canRestoreBackup: boolean;
  notice: string;
}) {
  const tools = (
    <FoodDebugTools
      recordCount={records.length}
      notice={notice}
      canUndoArchive={canUndoArchive}
      canRestoreBackup={canRestoreBackup}
      onLoadDemo={onLoadDemo}
      onQuickAdd={onQuickAdd}
      onArchiveFirst={onArchiveFirst}
      onUndoArchive={onUndoArchive}
      onExportBackup={onExportBackup}
      onRestoreBackup={onRestoreBackup}
    />
  );
  if (mode === 'Kitchen') return <><KitchenDemo records={kitchen.length ? kitchen : records} />{tools}</>;
  if (mode === 'Plan') return <><PlanDemo meals={meals} kitchen={kitchen} />{tools}</>;
  if (mode === 'Recipes') return <><RecipesDemo meals={meals} kitchen={kitchen} />{tools}</>;
  if (mode === 'Shop') return <><ShopDemo shopping={shopping} onToggleShopping={onToggleShopping} />{tools}</>;
  return <><TodayDemo meals={meals} kitchen={kitchen} shopping={shopping} reviewRows={reviewRows} />{tools}</>;
}

function FoodHeroPlate({ records, meals, kitchen, shopping, onAsk, onLoadDemo }: {
  records: FoodRecordView[];
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  onAsk: () => void;
  onLoadDemo: () => void;
}) {
  const theme = useLifeOSTheme();
  const dinner = meals[0]?.title ?? 'Salmon rice bowls';
  const useFirst = kitchen[0]?.title ?? 'Baby spinach';
  const missing = shopping[0]?.title ?? 'Rice vinegar';
  const hasRecords = records.length > 0;
  return (
    <View style={[styles.foodHeroPlate, { backgroundColor: theme.dark ? '#222018' : '#FFF2D8', borderColor: theme.dark ? '#3A3324' : '#F1D5A5' }]}>
      <View style={styles.foodHeroGlow} />
      <View style={styles.foodHeroArt} pointerEvents="none">
        <Text style={styles.foodHeroPlateEmoji}>🍣</Text>
        <Text style={styles.foodHeroLeafEmoji}>🥬</Text>
        <Text style={styles.foodHeroSparkEmoji}>✦</Text>
      </View>
      <View style={styles.foodHeroTop}>
        <Text style={[styles.foodHeroEyebrow, { color: theme.colors.amber }]}>WONDERFOOD</Text>
        <View style={[styles.foodHeroBadge, { backgroundColor: theme.dark ? '#342B1D' : '#FFE5B7' }]}>
          <Text style={[styles.foodHeroBadgeText, { color: theme.dark ? theme.colors.amber : '#8C4A18' }]}>{hasRecords ? `${records.length} items` : 'Fresh start'}</Text>
        </View>
      </View>
      <Text style={[styles.foodHeroTitle, { color: theme.colors.ink }]}>What should we cook next?</Text>
      <Text style={[styles.foodHeroBody, { color: theme.colors.muted }]}>Tonight looks like {dinner}. Use {useFirst} first, and grab {missing} if you want the bowl to sing.</Text>
      <View style={styles.foodHeroStats}>
        <FoodHeroStat label="Dinner" value={dinner} tone="moss" />
        <FoodHeroStat label="Use first" value={useFirst} tone="amber" />
        <FoodHeroStat label="Missing" value={missing} tone="blue" />
      </View>
      <View style={styles.foodHeroActions}>
        <Pressable accessibilityRole="button" accessibilityLabel="Ask WonderFood" onPress={onAsk} style={({ pressed }) => [styles.foodHeroPrimary, { backgroundColor: theme.dark ? theme.colors.amber : '#251812' }, pressed && styles.pressed]}>
          <Text style={[styles.foodHeroPrimaryText, { color: theme.dark ? '#251812' : '#FFF8EA' }]}>Ask what to cook</Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Load sample kitchen" testID="food-load-demo-hero" onPress={onLoadDemo} style={({ pressed }) => [styles.foodHeroSecondary, { borderColor: theme.dark ? '#4C4334' : '#E8C993' }, pressed && styles.pressed]}>
          <Text style={[styles.foodHeroSecondaryText, { color: theme.colors.ink }]}>Try sample</Text>
        </Pressable>
      </View>
    </View>
  );
}

function FoodHeroStat({ label, value, tone }: { label: string; value: string; tone: FoodTone }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.foodHeroStat, foodToneStyle(tone, theme.colors)]}>
      <Text style={[styles.foodHeroStatLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text style={[styles.foodHeroStatValue, { color: theme.colors.ink }]} numberOfLines={1}>{value}</Text>
    </View>
  );
}

function TodayDemo({ meals, kitchen, shopping, reviewRows }: {
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  reviewRows: FoodRecordView[];
}) {
  const liveMeals = meals.slice(0, 3).map((record, index) => ({
    title: `${['Breakfast', 'Lunch', 'Dinner'][index] ?? 'Meal'}: ${record.title}`,
    detail: record.body || record.meta,
    badge: record.status || (index === 0 ? '+22g protein' : index === 1 ? 'use first' : 'draft'),
    tone: index === 0 ? 'moss' as const : index === 1 ? 'amber' as const : 'red' as const,
    href: `/record/${record.id}`,
  }));
  const rows = liveMeals.length >= 3 ? liveMeals : fallbackMeals;
  const review = reviewRows[0];
  const useSoon = kitchen[0];
  const shop = shopping[0];
  return (
    <View style={styles.foodDemoStack}>
      <FoodSummaryCard
        tone="neutral"
        glyphTone="moss"
        glyph="✨"
        title="Today feels handled"
        detail={`${rows.length + 1} meals shaped around what is already in your kitchen`}
        badge={`${Math.max(reviewRows.length, 3)} ideas`}
      />
      {rows.map((row) => (
        <FoodDemoRow key={row.title} {...row} />
      ))}
      <FoodActionCard
        tone="moss"
        title="Ready to save"
        detail={review?.meta || 'Found a grocery receipt and cleaned it into food items.'}
        strong={review ? review.title : 'Salmon, blueberries, spinach'}
        note="Edit anything first. The default is the safest good version."
        cta="Looks good"
        href={review ? `/record/${review.id}` : '/chat'}
      />
      <FoodActionCard
        tone="neutral"
        title="Tiny kitchen magic"
        detail={useSoon ? `Cook around ${useSoon.title}.` : 'Cook lentil soup tomorrow: uses carrots, celery, and stock expiring soon.'}
        strong={shop ? `Missing: ${shop.title}` : undefined}
      />
    </View>
  );
}

function KitchenDemo({ records }: { records: FoodRecordView[] }) {
  const rows = records.slice(0, 5).map((record, index) => ({
    title: record.title,
    detail: record.body || record.meta,
    badge: record.status || fallbackKitchen[index]?.badge || 'ready',
    tone: fallbackKitchen[index]?.tone ?? ('moss' as const),
    href: `/record/${record.id}`,
  }));
  const resolvedRows = rows.length ? rows : fallbackKitchen;
  return (
    <View style={styles.foodDemoStack}>
      <View style={styles.foodChipRow}>
        <FoodFilterChip label={`Use first ${Math.max(5, resolvedRows.filter((item) => item.tone === 'amber').length)}`} tone="amber" selected />
        <FoodFilterChip label={`Pantry ${Math.max(18, resolvedRows.length)}`} tone="moss" />
        <FoodFilterChip label="Freezer 6" tone="blue" />
      </View>
      {resolvedRows.map((row) => <FoodDemoRow key={row.title} {...row} />)}
      <FoodActionCard
        tone="amber"
        title="Use-first shelf"
        detail="The food that should not be wasted rises to the top, with dinner ideas attached."
        cta="Plan from this"
        href="/chat"
      />
    </View>
  );
}

function PlanDemo({ meals, kitchen }: { meals: FoodRecordView[]; kitchen: FoodRecordView[] }) {
  const days = ['Fri', 'Sat', 'Sun', 'Mon', 'Tue', 'Wed', 'Thu'];
  return (
    <View style={styles.foodDemoStack}>
      <View style={styles.foodWeekStrip}>
        {days.map((day, index) => (
          <View key={day} style={[styles.foodDay, index === 0 && styles.foodDayActive]}>
            <Text style={styles.foodDayName}>{day}</Text>
            <Text style={styles.foodDayDot}>{index < 4 ? '•' : '○'}</Text>
          </View>
        ))}
      </View>
      {(meals.length ? meals : []).slice(0, 4).map((record, index) => (
        <FoodDemoRow
          key={record.id}
          title={`${days[index] ?? 'Plan'}: ${record.title}`}
          detail={record.body || record.meta}
          badge={index === 0 ? 'tonight' : index === 1 ? 'planned' : 'draft'}
          tone={index === 0 ? 'moss' : index === 1 ? 'blue' : 'amber'}
          href={`/record/${record.id}`}
        />
      ))}
      {!meals.length ? fallbackMeals.map((row, index) => <FoodDemoRow key={row.title} title={`${days[index]}: ${row.title.replace(/^.*?: /, '')}`} detail={row.detail} badge={row.badge} tone={row.tone} />) : null}
      <FoodActionCard
        tone="blue"
        title="Plan with my kitchen"
        detail={kitchen[0] ? `Start from ${kitchen[0].title}, then build meals and a tiny shopping gap.` : 'Draft a week from pantry, habits, and cravings.'}
        cta="Make my week"
        href="/chat"
      />
    </View>
  );
}

function RecipesDemo({ meals, kitchen }: { meals: FoodRecordView[]; kitchen: FoodRecordView[] }) {
  const recipes = (meals.length ? meals : []).slice(0, 4);
  return (
    <View style={styles.foodDemoStack}>
      <View style={styles.foodChipRow}>
        <FoodFilterChip label="Make now" tone="moss" selected />
        <FoodFilterChip label="Almost" tone="amber" />
        <FoodFilterChip label="All recipes" tone="blue" />
      </View>
      {recipes.map((record, index) => (
        <FoodDemoRow
          key={record.id}
          title={record.title}
          detail={record.body || record.meta || `Matches ${kitchen[index % Math.max(kitchen.length, 1)]?.title ?? 'pantry'} items.`}
          badge={index === 0 ? 'have all' : index === 1 ? 'need 1' : 'scale'}
          tone={index === 0 ? 'moss' : index === 1 ? 'amber' : 'blue'}
          href={`/record/${record.id}`}
        />
      ))}
      {!recipes.length ? fallbackMeals.map((row, index) => (
        <FoodDemoRow key={row.title} title={row.title.replace(/^.*?: /, '')} detail={row.detail} badge={index === 0 ? 'have all' : index === 1 ? 'need 1' : 'draft'} tone={row.tone} />
      )) : null}
      <FoodActionCard
        tone="moss"
        title="Recipe matching"
        detail="Recipes are grouped by how close they are to dinner: make now, almost, and worth one errand."
        cta="Surprise me"
        href="/chat"
      />
    </View>
  );
}

function ShopDemo({ shopping, onToggleShopping }: { shopping: FoodRecordView[]; onToggleShopping: (record: FoodRecordView) => void }) {
  const rows = shopping.slice(0, 3);
  return (
    <View style={styles.foodDemoStack}>
      <View style={styles.foodChipRow}>
        <FoodFilterChip label={`To buy ${Math.max(7, rows.length + 4)}`} tone="amber" selected />
        <FoodFilterChip label="Receipts 2" tone="neutral" />
        <FoodFilterChip label={`Put away ${Math.max(4, rows.length)}`} tone="moss" />
      </View>
      <FoodActionCard
        tone="neutral"
        title="Grocery receipt"
        detail="Market Basket found likely groceries."
        strong="Salmon, spinach, blueberries"
        cta="Review basket"
        href="/chat"
        ctaTone="red"
      />
      {rows.length ? rows.map((record, index) => (
        <FoodDemoRow
          key={record.id}
          title={record.title}
          detail={record.body || record.meta}
          badge={record.status}
          tone={index === 0 ? 'moss' : index === 1 ? 'amber' : 'blue'}
          href={`/record/${record.id}`}
          onPress={() => onToggleShopping(record)}
        />
      )) : fallbackShop.map((row) => <FoodDemoRow key={row.title} {...row} />)}
      <FoodActionCard
        tone="blue"
        title="AI made a list"
        detail="WonderFood found likely groceries. Accept, edit, or toss it."
        actions={['Accept', 'Edit', 'Reject']}
      />
    </View>
  );
}

function FoodSummaryCard({ glyphTone, glyph, title, detail, badge }: { tone: 'neutral'; glyphTone: FoodTone; glyph: string; title: string; detail: string; badge: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.foodSummaryCard, { backgroundColor: theme.dark ? theme.colors.paper : '#EDE8DD' }]}>
      <View style={[styles.foodGlyph, foodToneStyle(glyphTone, theme.colors)]}>
        <Text style={styles.foodGlyphText}>{glyph}</Text>
      </View>
      <View style={styles.foodRowCopy}>
        <Text style={[styles.foodRowTitle, { color: theme.colors.ink }]}>{title}</Text>
        <Text style={[styles.foodRowDetail, { color: theme.colors.muted }]}>{detail}</Text>
      </View>
      <View style={[styles.foodBadge, foodToneStyle('amber', theme.colors)]}>
        <Text style={[styles.foodBadgeText, { color: theme.colors.ink }]}>{badge}</Text>
      </View>
    </View>
  );
}

function FoodDemoRow({ title, detail, badge, tone, href, onPress }: {
  title: string;
  detail: string;
  badge: string;
  tone: FoodTone;
  href?: string;
  onPress?: () => void;
}) {
  const theme = useLifeOSTheme();
  const content = (
    <View style={[styles.foodDemoRow, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
      <View style={[styles.foodGlyph, foodToneStyle(tone, theme.colors)]}>
        <Text style={styles.foodGlyphText}>{tone === 'amber' ? '!' : tone === 'red' ? '•' : tone === 'blue' ? '↗' : '✓'}</Text>
      </View>
      <View style={styles.foodRowCopy}>
        <Text style={[styles.foodRowTitle, { color: theme.colors.ink }]} numberOfLines={1}>{title}</Text>
        <Text style={[styles.foodRowDetail, { color: theme.colors.muted }]} numberOfLines={2}>{detail}</Text>
      </View>
      <View style={[styles.foodBadge, foodToneStyle(tone, theme.colors)]}>
        <Text style={[styles.foodBadgeText, { color: tone === 'red' ? theme.colors.red : theme.colors.moss }]} numberOfLines={1}>{badge}</Text>
      </View>
    </View>
  );
  if (href && !onPress) {
    return (
      <Link href={href as never} asChild>
        <Pressable accessibilityRole="button" accessibilityLabel={`Open ${title}`} testID={`food-row-${slugId(title)}`} style={({ pressed }) => [styles.foodRowPress, pressed && styles.pressed]}>
          {content}
        </Pressable>
      </Link>
    );
  }
  return <Pressable accessibilityRole="button" accessibilityLabel={onPress ? `Toggle ${title}` : title} testID={`food-row-${slugId(title)}`} onPress={onPress} style={({ pressed }) => [styles.foodRowPress, pressed && styles.pressed]}>{content}</Pressable>;
}

function FoodActionCard({ title, detail, strong, note, cta, href, tone, ctaTone = 'moss', actions }: {
  title: string;
  detail: string;
  strong?: string;
  note?: string;
  cta?: string;
  href?: string;
  tone: 'neutral' | 'moss' | 'amber' | 'blue';
  ctaTone?: FoodTone;
  actions?: string[];
}) {
  const theme = useLifeOSTheme();
  const [actionNotice, setActionNotice] = useState('');
  const card = (
    <View style={[styles.foodActionCard, foodPanelStyle(tone, theme.colors)]}>
      <Text style={[styles.foodActionTitle, { color: theme.colors.ink }]}>{title}</Text>
      <Text style={[styles.foodActionDetail, { color: theme.colors.muted }]}>{detail}</Text>
      {strong ? <Text style={[styles.foodActionStrong, { color: theme.colors.ink }]}>{strong}</Text> : null}
      {note ? <Text style={[styles.foodActionNote, { color: theme.colors.muted }]}>{note}</Text> : null}
      {actions ? (
        <View style={styles.foodActionButtons}>
          {actions.map((action, index) => (
            <Pressable
              key={action}
              accessibilityRole="button"
              accessibilityLabel={`${action} ${title}`}
              testID={`food-action-${slugId(action)}-${slugId(title)}`}
              onPress={() => setActionNotice(`${action} recorded for ${title}.`)}
              style={({ pressed }) => [styles.foodActionButton, index === 0 ? { backgroundColor: theme.colors.moss } : foodToneStyle(index === 1 ? 'neutral' : 'red', theme.colors), pressed && styles.pressed]}
            >
              <Text style={[styles.foodActionButtonText, { color: index === 0 ? theme.colors.paper : index === 2 ? theme.colors.red : theme.colors.ink }]}>{action}</Text>
            </Pressable>
          ))}
        </View>
      ) : cta ? (
        <View style={[styles.foodInlineCta, foodToneStyle(ctaTone, theme.colors)]}>
          <Text style={[styles.foodInlineCtaText, { color: ctaTone === 'red' ? theme.colors.red : theme.colors.moss }]}>{cta}</Text>
        </View>
      ) : null}
      {actionNotice ? <Text style={[styles.foodActionNote, { color: theme.colors.moss }]}>{actionNotice}</Text> : null}
    </View>
  );
  return href ? <Link href={href as never} asChild><Pressable accessibilityRole="button" style={({ pressed }) => [pressed && styles.pressed]}>{card}</Pressable></Link> : card;
}

function FoodDebugTools({ recordCount, notice, canUndoArchive, canRestoreBackup, onLoadDemo, onQuickAdd, onArchiveFirst, onUndoArchive, onExportBackup, onRestoreBackup }: {
  recordCount: number;
  notice: string;
  canUndoArchive: boolean;
  canRestoreBackup: boolean;
  onLoadDemo: () => void;
  onQuickAdd: (collection: string, title: string, properties: Record<string, unknown>) => void;
  onArchiveFirst: () => void;
  onUndoArchive: () => void;
  onExportBackup: () => void;
  onRestoreBackup: () => void;
}) {
  const theme = useLifeOSTheme();
  const [expanded, setExpanded] = useState(false);
  const addPantry = () => onQuickAdd('inventory', 'Baby spinach', { status: 'Use first', body: 'Fridge. Great for wraps, eggs, or rice bowls.', meta: '2 days left', quantity: '1 clamshell' });
  const addMeal = () => onQuickAdd('meal_plan', 'Salmon rice bowls', { status: 'Planned', body: 'Dinner plan from pantry plus shopping gaps.', meta: 'Tonight', planned_for: new Date().toISOString().slice(0, 10) });
  const addShopping = () => onQuickAdd('shopping_item', 'Rice vinegar', { status: 'To buy', body: 'Needed for salmon rice bowls.', meta: 'Missing ingredient' });
  if (!expanded) {
    return (
      <View style={styles.foodLabCollapsed}>
        {notice ? <Text style={[styles.foodLabNotice, { color: theme.colors.moss }]} testID="food-debug-notice">{notice}</Text> : null}
        <Pressable accessibilityRole="button" accessibilityLabel="Add food" testID="food-show-kitchen-lab" onPress={() => setExpanded(true)} style={({ pressed }) => [styles.foodLabButton, { backgroundColor: theme.dark ? theme.colors.paper : '#FFF8EC', borderColor: theme.colors.line }, pressed && styles.pressed]}>
          <Text style={[styles.foodLabButtonText, { color: theme.colors.muted }]}>Add food</Text>
        </Pressable>
      </View>
    );
  }
  return (
    <View style={[styles.foodDebugTools, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]} testID="food-debug-tools">
      <View style={styles.foodDebugHeader}>
        <View>
          <Text style={[styles.foodActionTitle, { color: theme.colors.ink }]}>Start your kitchen</Text>
          <Text style={[styles.foodActionDetail, { color: theme.colors.muted }]}>{recordCount ? `${recordCount} saved food items` : 'No meals yet. Add your first pantry item or load the demo household.'}</Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Load demo household" testID="food-load-demo" onPress={onLoadDemo} style={({ pressed }) => [styles.foodDebugPrimary, { backgroundColor: theme.colors.ink }, pressed && styles.pressed]}>
          <Text style={[styles.foodActionButtonText, { color: theme.colors.paper }]}>Sample</Text>
        </Pressable>
      </View>
      <View style={styles.foodActionButtons}>
        <Pressable accessibilityRole="button" accessibilityLabel="Add pantry item" testID="food-add-pantry" onPress={addPantry} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('moss', theme.colors), pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.moss }]}>Pantry</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Add meal plan" testID="food-add-meal" onPress={addMeal} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('blue', theme.colors), pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.blue }]}>Meal</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Add shopping item" testID="food-add-shopping" onPress={addShopping} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('amber', theme.colors), pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.ink }]}>Shop</Text></Pressable>
      </View>
      <View style={styles.foodActionButtons}>
        <Pressable accessibilityRole="button" accessibilityLabel="Archive first visible food record" testID="food-archive-first" onPress={onArchiveFirst} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('red', theme.colors), pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.red }]}>Archive</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Undo last archive" testID="food-undo-archive" disabled={!canUndoArchive} onPress={onUndoArchive} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('neutral', theme.colors), !canUndoArchive && styles.disabled, pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.ink }]}>Undo</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Export local backup" testID="food-export-backup" onPress={onExportBackup} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('blue', theme.colors), pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.blue }]}>Export</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Restore local backup" testID="food-restore-backup" disabled={!canRestoreBackup} onPress={onRestoreBackup} style={({ pressed }) => [styles.foodActionButton, foodToneStyle('neutral', theme.colors), !canRestoreBackup && styles.disabled, pressed && styles.pressed]}><Text style={[styles.foodActionButtonText, { color: theme.colors.ink }]}>Restore</Text></Pressable>
      </View>
      {notice ? <Text style={[styles.foodActionNote, { color: theme.colors.moss }]} testID="food-debug-notice">{notice}</Text> : null}
    </View>
  );
}

function FoodFilterChip({ label, tone, selected }: { label: string; tone: FoodTone | 'neutral'; selected?: boolean }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.foodFilterChip, foodToneStyle(tone, theme.colors), selected && styles.foodFilterChipSelected]}>
      <Text style={[styles.foodFilterText, { color: tone === 'blue' ? theme.colors.blue : tone === 'amber' ? '#775E00' : theme.colors.moss }]}>{label}</Text>
    </View>
  );
}

function FoodDemoNav({ active, onChange }: { active: FoodMode; onChange: (mode: FoodMode) => void }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.foodDemoNav, { backgroundColor: theme.dark ? '#171B14' : '#F1F6EE' }]}>
      {FOOD_MODES.map((mode) => {
        const selected = active === mode.id;
        return (
          <Pressable key={mode.id} accessibilityRole="tab" accessibilityLabel={`Food ${mode.label} tab`} testID={`food-tab-${mode.id.toLowerCase()}`} accessibilityState={{ selected }} onPress={() => onChange(mode.id)} style={styles.foodNavItem}>
            <View style={[styles.foodNavGlyph, selected && { backgroundColor: theme.colors.amberSoft }]}>
              <Text style={[styles.foodNavGlyphText, { color: selected ? theme.colors.moss : theme.colors.muted }]}>{mode.glyph}</Text>
            </View>
            <Text style={[styles.foodNavLabel, { color: selected ? theme.colors.moss : theme.colors.ink }]}>{mode.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function foodToneStyle(tone: FoodTone | 'neutral', themed: typeof colors) {
  if (tone === 'amber') return { backgroundColor: themed.amberSoft, borderColor: '#EAD58C' };
  if (tone === 'blue') return { backgroundColor: themed.blueSoft, borderColor: '#C8E2EF' };
  if (tone === 'plum') return { backgroundColor: themed.plumSoft, borderColor: themed.line };
  if (tone === 'red') return { backgroundColor: '#FFD8CD', borderColor: '#F5C5B7' };
  if (tone === 'neutral') return { backgroundColor: themed.canvas, borderColor: themed.line };
  return { backgroundColor: themed.mossSoft, borderColor: '#CBEACD' };
}

function foodPanelStyle(tone: 'neutral' | 'moss' | 'amber' | 'blue', themed: typeof colors) {
  if (tone === 'moss') return { backgroundColor: '#EDF8EF', borderColor: '#CBEACD' };
  if (tone === 'amber') return { backgroundColor: themed.amberSoft, borderColor: '#EAD58C' };
  if (tone === 'blue') return { backgroundColor: themed.blueSoft, borderColor: '#C8E2EF' };
  return { backgroundColor: themed.paper, borderColor: themed.line };
}

function RecordColumn({ title, subtitle, records, visuals, empty }: {
  title: string;
  subtitle: string;
  records: FoodRecordView[];
  visuals: Record<string, VisualToken>;
  empty: string;
}) {
  const theme = useLifeOSTheme();
  return (
    <Card style={styles.column}>
      <Text style={[styles.columnTitle, { color: theme.colors.ink }]}>{title}</Text>
      <Text style={[styles.columnSubtitle, { color: theme.colors.muted }]}>{subtitle}</Text>
      <View style={styles.columnRecords}>
        {records.length ? records.map((record) => <MiniRecord key={record.id} record={record} visual={visuals[record.collection]} />) : (
          <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>{empty}</Text>
        )}
      </View>
    </Card>
  );
}

function FoodOperatingViews({ meals, kitchen, shopping, compact, order, onToggleShopping }: {
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  compact: boolean;
  order: OperatingViewSection[];
  onToggleShopping: (record: FoodRecordView) => void;
}) {
  const renderView = (section: OperatingViewSection) => {
    if (section === 'assemblyTable') return <DinnerAssemblyTable key={section} meals={meals} kitchen={kitchen} shopping={shopping} compact={compact} />;
    if (section === 'weekPlan') return <MealWeekPlan key={section} records={meals} />;
    if (section === 'pantryTimeline') return <PantryTimeline key={section} records={kitchen} />;
    return <ShoppingChecklist key={section} records={shopping} onToggle={onToggleShopping} />;
  };

  return (
    <View style={[styles.operatingViews, compact && styles.operatingViewsCompact]}>
      {order.map(renderView)}
    </View>
  );
}

function DinnerAssemblyTable({ meals, kitchen, shopping, compact }: {
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  compact: boolean;
}) {
  const theme = useLifeOSTheme();
  const rows = meals.slice(0, compact ? 2 : 4).map((meal, index) => {
    const available = kitchen[index % Math.max(kitchen.length, 1)];
    const missing = shopping[index % Math.max(shopping.length, 1)];
    return {
      meal,
      available,
      missing,
      action: missing ? `Resolve ${missing.title}` : 'Ready to cook',
    };
  });

  return (
    <Card tone="moss" style={[styles.assemblyCard, compact && styles.assemblyCardCompact]}>
      <View style={styles.assemblyHead}>
        <View>
          <Text style={[styles.operatingLabel, { color: theme.colors.muted }]}>Tonight operating table</Text>
          <Text style={[styles.operatingTitle, { color: theme.colors.ink }]}>Cook decision, pantry state, shopping gap, next move</Text>
        </View>
        <Link href="/chat" style={[styles.assemblyAsk, { color: theme.colors.moss }]}>Ask with this table →</Link>
      </View>
      <View style={[styles.assemblyTable, { borderColor: theme.colors.line }]}>
        {!compact ? (
          <View style={[styles.assemblyRow, styles.assemblyHeaderRow, { borderBottomColor: theme.colors.line }]}>
            <Text style={[styles.assemblyHeader, { color: theme.colors.muted }]}>Meal / recipe</Text>
            <Text style={[styles.assemblyHeader, { color: theme.colors.muted }]}>Available</Text>
            <Text style={[styles.assemblyHeader, { color: theme.colors.muted }]}>Need / cart</Text>
            <Text style={[styles.assemblyHeader, { color: theme.colors.muted }]}>Action</Text>
          </View>
        ) : null}
        {rows.length ? rows.map((row) => (
          <Link key={row.meal.id} href={{ pathname: '/record/[id]', params: { id: row.meal.id } }} asChild>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.assemblyRow, compact && styles.assemblyRowCompact, { borderBottomColor: theme.colors.line }, pressed && styles.pressed]}>
              <AssemblyCell label={compact ? 'Meal / recipe' : undefined} title={row.meal.title} detail={row.meal.meta} tone="moss" />
              <AssemblyCell label={compact ? 'Available' : undefined} title={row.available?.title ?? 'Not captured'} detail={row.available?.meta ?? 'Add pantry item'} tone="amber" />
              <AssemblyCell label={compact ? 'Need / cart' : undefined} title={row.missing?.title ?? 'Nothing missing'} detail={row.missing?.meta ?? 'No shopping blocker'} tone="blue" />
              <AssemblyCell label={compact ? 'Action' : undefined} title={row.action} detail={row.missing ? 'Open item or ask AI to substitute' : 'Open and log cooking'} tone={row.missing ? 'plum' : 'moss'} />
            </Pressable>
          </Link>
        )) : (
          <View style={styles.assemblyEmpty}>
            <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>No dinner table yet</Text>
            <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>Capture a meal plan, recipe, pantry item or shopping need.</Text>
          </View>
        )}
      </View>
    </Card>
  );
}

function AssemblyCell({ label, title, detail, tone }: {
  label?: string;
  title: string;
  detail: string;
  tone: 'moss' | 'amber' | 'blue' | 'plum';
}) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.assemblyCell}>
      {label ? <Text style={[styles.assemblyCellLabel, { color: theme.colors.muted }]}>{label}</Text> : null}
      <View style={[styles.assemblyDot, commandToneStyle(tone, theme.colors)]} />
      <Text style={[styles.assemblyTitle, { color: theme.colors.ink }]} numberOfLines={1}>{title}</Text>
      <Text style={[styles.assemblyDetail, { color: theme.colors.muted }]} numberOfLines={1}>{detail}</Text>
    </View>
  );
}

function CollectionStat({ label, value, tone }: { label: string; value: string; tone: 'moss' | 'amber' | 'blue' | 'plum' }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.collectionStat, commandToneStyle(tone, theme.colors)]}>
      <Text style={[styles.collectionStatValue, { color: theme.colors.ink }]} numberOfLines={1}>{value}</Text>
      <Text style={[styles.collectionStatLabel, { color: theme.colors.muted }]}>{label}</Text>
    </View>
  );
}

function CollectionGroup({ title, subtitle, collections, counts, visuals, tone }: {
  title: string;
  subtitle: string;
  collections: string[];
  counts: Record<string, number>;
  visuals: Record<string, VisualToken>;
  tone: 'moss' | 'amber' | 'blue' | 'plum';
}) {
  const theme = useLifeOSTheme();
  return (
    <Card tone={tone} style={styles.collectionGroup}>
      <View style={styles.collectionGroupHead}>
        <View style={styles.collectionGroupCopy}>
          <Text style={[styles.collectionGroupTitle, { color: theme.colors.ink }]}>{title}</Text>
          <Text style={[styles.collectionGroupSubtitle, { color: theme.colors.muted }]}>{subtitle}</Text>
        </View>
        <Pill tone={tone}>{collections.length} collections</Pill>
      </View>
      <View style={styles.collectionChips}>
        {collections.map((collection) => {
          const count = counts[collection] ?? 0;
          const token = visuals[collection];
          const accent = visualAccent(token);
          return (
            <Link key={collection} href={{ pathname: '/collection/[id]', params: { id: collection } }} asChild>
              <Pressable accessibilityRole="button" style={({ pressed }) => [styles.collectionChip, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
                <VisualMark token={token} fallback={collection.slice(0, 1).toUpperCase()} size={25} backgroundColor={softForAccent(accent, theme.colors)} color={inkForAccent(accent, theme.colors)} label={`${humanizeCollection(collection)} visual`} glyphStyle={styles.collectionChipGlyphText} />
                <Text style={[styles.collectionChipName, { color: theme.colors.ink }]}>{humanizeCollection(collection)}</Text>
                <Text style={[styles.collectionChipCount, { color: count ? theme.colors.moss : theme.colors.muted }]}>{count ? `${count}` : '—'}</Text>
              </Pressable>
            </Link>
          );
        })}
      </View>
    </Card>
  );
}

function MealWeekPlan({ records }: { records: FoodRecordView[] }) {
  const theme = useLifeOSTheme();
  const days = ['Thu', 'Fri', 'Sat', 'Sun'];
  return (
    <Card tone="moss" style={styles.operatingCard}>
      <Text style={[styles.operatingLabel, { color: theme.colors.muted }]}>Week plan</Text>
      <Text style={[styles.operatingTitle, { color: theme.colors.ink }]}>Dinner rhythm</Text>
      <View style={styles.weekRows}>
        {days.map((day, index) => {
          const record = records[index % Math.max(records.length, 1)];
          return (
            <Link key={day} href={record ? { pathname: '/record/[id]', params: { id: record.id } } : '/chat'} asChild>
              <Pressable accessibilityRole="button" style={({ pressed }) => [styles.weekRow, { borderTopColor: theme.colors.line }, pressed && styles.pressed]}>
                <Text style={[styles.dayPill, { backgroundColor: theme.colors.paper, color: theme.colors.moss }]}>{day}</Text>
                <View style={styles.weekCopy}>
                  <Text style={[styles.weekTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record?.title ?? 'Ask Food AI to plan'}</Text>
                  <Text style={[styles.weekDetail, { color: theme.colors.muted }]} numberOfLines={1}>{record?.meta ?? 'Draft from pantry and preferences'}</Text>
                </View>
              </Pressable>
            </Link>
          );
        })}
      </View>
    </Card>
  );
}

function PantryTimeline({ records }: { records: FoodRecordView[] }) {
  const theme = useLifeOSTheme();
  return (
    <Card tone="amber" style={styles.operatingCard}>
      <Text style={[styles.operatingLabel, { color: theme.colors.muted }]}>Pantry timeline</Text>
      <Text style={[styles.operatingTitle, { color: theme.colors.ink }]}>Use-first queue</Text>
      <View style={styles.timelineRows}>
        {(records.length ? records : []).slice(0, 4).map((record, index) => (
          <Link key={record.id} href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.timelineRow, pressed && styles.pressed]}>
              <View style={[styles.timelineDot, { backgroundColor: index === 0 ? theme.colors.amberSoft : theme.colors.paper, borderColor: theme.colors.line }]} />
              <View style={styles.weekCopy}>
                <Text style={[styles.weekTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
                <Text style={[styles.weekDetail, { color: theme.colors.muted }]} numberOfLines={1}>{record.status} · {record.meta}</Text>
              </View>
            </Pressable>
          </Link>
        ))}
        {!records.length ? <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>No pantry pressure yet.</Text> : null}
      </View>
    </Card>
  );
}

function ShoppingChecklist({ records, onToggle }: { records: FoodRecordView[]; onToggle: (record: FoodRecordView) => void }) {
  const theme = useLifeOSTheme();
  return (
    <Card tone="blue" style={styles.operatingCard}>
      <Text style={[styles.operatingLabel, { color: theme.colors.muted }]}>Shopping checklist</Text>
      <Text style={[styles.operatingTitle, { color: theme.colors.ink }]}>Buy with reasons</Text>
      <View style={styles.checkRows}>
        {records.length ? records.map((record) => (
          <View key={record.id} style={[styles.checkRow, { borderTopColor: theme.colors.line }]}>
            <Pressable accessibilityRole="checkbox" accessibilityLabel={`Mark ${record.title} as in cart`} testID={`food-check-${slugId(record.title)}`} accessibilityState={{ checked: /in cart|bought/i.test(record.status) }} onPress={() => onToggle(record)} style={({ pressed }) => [styles.checkTap, pressed && styles.pressed]}>
              <View style={[styles.checkBox, /in cart|bought/i.test(record.status) && styles.checkBoxDone, { borderColor: theme.colors.moss, backgroundColor: /in cart|bought/i.test(record.status) ? theme.colors.mossSoft : theme.colors.paper }]}>
                {/in cart|bought/i.test(record.status) ? <Text style={[styles.checkMark, { color: theme.colors.moss }]}>✓</Text> : null}
              </View>
            </Pressable>
            <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
              <Pressable accessibilityRole="button" style={({ pressed }) => [styles.checkCopy, pressed && styles.pressed]}>
              <View style={styles.weekCopy}>
                <Text style={[styles.weekTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
                <Text style={[styles.weekDetail, { color: theme.colors.muted }]} numberOfLines={1}>{record.status} · {record.body || record.meta}</Text>
              </View>
              </Pressable>
            </Link>
          </View>
        )) : (
          <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>Nothing to buy.</Text>
        )}
      </View>
    </Card>
  );
}

function MiniRecord({ record, visual }: { record: FoodRecordView; visual?: VisualToken }) {
  const theme = useLifeOSTheme();
  const accent = visualAccent(visual);
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.miniRecord, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
        <VisualMark token={visual} fallback={record.collection.slice(0, 1).toUpperCase()} size={34} backgroundColor={softForAccent(accent, theme.colors)} color={inkForAccent(accent, theme.colors)} label={`${record.collection} visual`} style={styles.miniGlyph} glyphStyle={styles.miniGlyphText} />
        <View style={styles.miniTop}>
          <Text style={[styles.miniTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
          <Pill tone={record.tone}>{record.status}</Pill>
        </View>
        <Text style={[styles.recordMeta, { color: theme.colors.muted }]} numberOfLines={1}>{record.meta}</Text>
        <Text style={[styles.recordBody, { color: theme.colors.ink }]} numberOfLines={2}>{record.body}</Text>
      </Pressable>
    </Link>
  );
}

function RecordListItem({ record, visual }: { record: FoodRecordView; visual?: VisualToken }) {
  const theme = useLifeOSTheme();
  const accent = visualAccent(visual);
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable style={({ pressed }) => [styles.record, { borderBottomColor: theme.colors.line }, pressed && styles.pressed]}>
        <VisualMark token={visual} fallback={record.collection.slice(0, 1).toUpperCase()} size={42} backgroundColor={softForAccent(accent, theme.colors)} color={inkForAccent(accent, theme.colors)} label={`${record.collection} visual`} style={styles.recordIcon} glyphStyle={styles.recordIconText} />
        <View style={styles.recordCopy}>
          <View style={styles.recordTop}>
            <Text style={[styles.recordTitle, { color: theme.colors.ink }]}>{record.title}</Text>
            <Pill tone={record.tone}>{record.status}</Pill>
          </View>
          <Text style={[styles.recordMeta, { color: theme.colors.muted }]}>{record.meta}</Text>
          <Text style={[styles.recordBody, { color: theme.colors.ink }]} numberOfLines={2}>{record.body}</Text>
        </View>
      </Pressable>
    </Link>
  );
}

function FeatureCard({ tone, label, item, fallbackTitle, fallbackBody }: {
  tone: 'moss' | 'amber' | 'blue' | 'plum';
  label: string;
  item?: FoodRecordView;
  fallbackTitle: string;
  fallbackBody: string;
}) {
  const theme = useLifeOSTheme();
  return (
    <Link href={item ? { pathname: '/record/[id]', params: { id: item.id } } : '/capture'} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.featurePress, pressed && styles.pressed]}>
        <Card tone={tone} style={styles.featureCard}>
          <Text style={[styles.featureLabel, { color: theme.colors.muted }]}>{label}</Text>
          <Text style={[styles.featureTitle, { color: theme.colors.ink }]}>{item?.title ?? fallbackTitle}</Text>
          <Text style={[styles.featureBody, { color: theme.colors.muted }]}>{item?.meta || item?.body || fallbackBody}</Text>
        </Card>
      </Pressable>
    </Link>
  );
}

function CommandStep({ index, title, detail, tone, href }: {
  index: string;
  title: string;
  detail: string;
  tone: 'moss' | 'amber' | 'blue' | 'plum';
  href: string;
}) {
  const theme = useLifeOSTheme();
  return (
    <Link href={href as never} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.commandStep, commandToneStyle(tone, theme.colors), pressed && styles.pressed]}>
        <Text style={[styles.commandIndex, { color: theme.colors.muted }]}>{index}</Text>
        <View style={styles.commandCopy}>
          <Text style={[styles.commandTitle, { color: theme.colors.ink }]}>{title}</Text>
          <Text style={[styles.commandDetail, { color: theme.colors.muted }]} numberOfLines={1}>{detail}</Text>
        </View>
      </Pressable>
    </Link>
  );
}

function commandToneStyle(tone: 'moss' | 'amber' | 'blue' | 'plum', themed: typeof colors) {
  if (tone === 'amber') return { backgroundColor: themed.amberSoft, borderColor: themed.line };
  if (tone === 'blue') return { backgroundColor: themed.blueSoft, borderColor: themed.line };
  if (tone === 'plum') return { backgroundColor: themed.plumSoft, borderColor: themed.line };
  return { backgroundColor: themed.mossSoft, borderColor: themed.line };
}

const styles = StyleSheet.create({
  foodDemoRoot: { flex: 1 },
  foodDemoContent: { alignSelf: 'center', maxWidth: 760, paddingHorizontal: 24, paddingTop: 34, paddingBottom: 158 },
  foodDemoContentCompact: { alignSelf: 'stretch', paddingHorizontal: 16, paddingTop: 26 },
  foodDemoTopbar: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, marginBottom: 22 },
  foodDemoHeadline: { flex: 1, minWidth: 0 },
  foodDemoTitle: { color: colors.ink, fontSize: 32, lineHeight: 36, fontWeight: '900', letterSpacing: -1.4 },
  foodDemoSubtitle: { color: colors.muted, fontSize: 16, lineHeight: 22, marginTop: 5, fontWeight: '600' },
  foodDemoTopActions: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingTop: 1, flexShrink: 0 },
  foodDemoIconButton: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 22 },
  foodDemoIcon: { minWidth: 40, minHeight: 40, color: colors.ink, fontSize: 24, lineHeight: 40, textAlign: 'center', fontWeight: '900', borderRadius: 20 },
  foodAskPill: { minWidth: 84, minHeight: 44, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 14 },
  foodAskPillCompact: { minWidth: 58, paddingHorizontal: 10 },
  foodDemoStack: { gap: 14 },
  foodHeroPlate: { position: 'relative', overflow: 'hidden', borderWidth: 1, borderRadius: 32, padding: 22, marginBottom: 18 },
  foodHeroGlow: { position: 'absolute', right: -64, top: -70, width: 180, height: 180, borderRadius: 90, backgroundColor: 'rgba(255, 177, 93, 0.34)' },
  foodHeroArt: { position: 'absolute', right: 14, top: 58, width: 150, height: 150, opacity: 0.2 },
  foodHeroPlateEmoji: { position: 'absolute', right: 0, top: 18, fontSize: 76, lineHeight: 86, transform: [{ rotate: '-10deg' }] },
  foodHeroLeafEmoji: { position: 'absolute', left: 12, top: 0, fontSize: 42, lineHeight: 48, transform: [{ rotate: '16deg' }] },
  foodHeroSparkEmoji: { position: 'absolute', left: 48, bottom: 8, color: colors.amber, fontSize: 42, lineHeight: 48, fontWeight: '900' },
  foodHeroTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  foodHeroEyebrow: { color: colors.amber, fontSize: 12, lineHeight: 15, fontWeight: '900', letterSpacing: 1.7 },
  foodHeroBadge: { borderRadius: radius.pill, paddingHorizontal: 12, paddingVertical: 7 },
  foodHeroBadgeText: { color: '#8C4A18', fontSize: 12, lineHeight: 14, fontWeight: '900' },
  foodHeroTitle: { color: colors.ink, fontSize: 38, lineHeight: 40, fontWeight: '900', letterSpacing: -1.6, marginTop: 30, maxWidth: 520 },
  foodHeroBody: { color: colors.muted, fontSize: 17, lineHeight: 25, marginTop: 12, maxWidth: 590 },
  foodHeroStats: { flexDirection: 'row', gap: 10, marginTop: 22, flexWrap: 'wrap' },
  foodHeroStat: { flexGrow: 1, flexBasis: 145, minHeight: 74, borderWidth: 1, borderRadius: 20, padding: 13, justifyContent: 'center' },
  foodHeroStatLabel: { color: colors.muted, fontSize: 10, lineHeight: 12, fontWeight: '900', letterSpacing: 1.1, textTransform: 'uppercase' },
  foodHeroStatValue: { color: colors.ink, fontSize: 15, lineHeight: 19, fontWeight: '900', marginTop: 7 },
  foodHeroActions: { flexDirection: 'row', gap: 10, marginTop: 20, flexWrap: 'wrap' },
  foodHeroPrimary: { flexGrow: 1, minHeight: 52, borderRadius: radius.pill, paddingHorizontal: 18, alignItems: 'center', justifyContent: 'center' },
  foodHeroPrimaryText: { color: '#FFF8EA', fontSize: 16, fontWeight: '900' },
  foodHeroSecondary: { minHeight: 52, borderRadius: radius.pill, borderWidth: 1, paddingHorizontal: 18, alignItems: 'center', justifyContent: 'center' },
  foodHeroSecondaryText: { color: colors.ink, fontSize: 16, fontWeight: '900' },
  foodLabCollapsed: { marginTop: 16, alignItems: 'center', gap: 8 },
  foodLabNotice: { fontSize: 13, lineHeight: 18, fontWeight: '800', textAlign: 'center' },
  foodLabButton: { minHeight: 42, borderRadius: radius.pill, borderWidth: 1, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center' },
  foodLabButtonText: { fontSize: 13, lineHeight: 17, fontWeight: '900' },
  foodRowPress: { alignSelf: 'stretch' },
  foodSummaryCard: { minHeight: 96, borderRadius: 26, paddingHorizontal: 16, paddingVertical: 16, flexDirection: 'row', alignItems: 'center', gap: 12 },
  foodDemoRow: { width: '100%', minHeight: 98, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, borderRadius: 24, paddingHorizontal: 14, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 10, overflow: 'hidden' },
  foodGlyph: { width: 44, height: 44, borderRadius: 14, borderWidth: 1, alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
  foodGlyphText: { color: colors.moss, fontSize: 18, lineHeight: 22, fontWeight: '900' },
  foodRowCopy: { flex: 1, minWidth: 0, flexShrink: 1 },
  foodRowTitle: { color: colors.ink, fontSize: 18, lineHeight: 23, fontWeight: '900', letterSpacing: -0.45 },
  foodRowDetail: { color: colors.muted, fontSize: 14, lineHeight: 20, marginTop: 4 },
  foodBadge: { minWidth: 70, maxWidth: 104, minHeight: 34, borderRadius: radius.pill, paddingHorizontal: 9, paddingVertical: 7, alignItems: 'center', justifyContent: 'center', borderWidth: 1, flexShrink: 0 },
  foodBadgeText: { color: colors.moss, fontSize: 12, lineHeight: 14, fontWeight: '900', textAlign: 'center' },
  foodActionCard: { borderWidth: 1, borderColor: colors.line, borderRadius: 26, padding: 20, minHeight: 132 },
  foodActionTitle: { color: colors.ink, fontSize: 25, lineHeight: 30, fontWeight: '900', letterSpacing: -0.7 },
  foodActionDetail: { color: colors.muted, fontSize: 17, lineHeight: 24, marginTop: 8 },
  foodActionStrong: { color: colors.ink, fontSize: 18, lineHeight: 25, fontWeight: '900', marginTop: 10 },
  foodActionNote: { color: colors.muted, fontSize: 15, lineHeight: 21, marginTop: 8 },
  foodInlineCta: { alignSelf: 'flex-end', marginTop: 8, minHeight: 38, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 8, justifyContent: 'center' },
  foodInlineCtaText: { color: colors.moss, fontSize: 14, fontWeight: '900' },
  foodActionButtons: { flexDirection: 'row', gap: 18, marginTop: 28 },
  foodActionButton: { flex: 1, minHeight: 44, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  foodActionButtonText: { color: colors.ink, fontSize: 17, fontWeight: '900' },
  foodChipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginBottom: 14 },
  foodFilterChip: { minHeight: 52, borderRadius: radius.pill, paddingHorizontal: 18, paddingVertical: 12, borderWidth: 1, justifyContent: 'center' },
  foodFilterChipSelected: { transform: [{ scale: 1.01 }] },
  foodFilterText: { color: colors.moss, fontSize: 18, fontWeight: '900' },
  foodWeekStrip: { flexDirection: 'row', gap: 10, marginBottom: 10 },
  foodDay: { flex: 1, minHeight: 76, borderRadius: 22, backgroundColor: colors.paper, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center' },
  foodDayActive: { backgroundColor: colors.amberSoft, borderColor: '#EAD58C' },
  foodDayName: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  foodDayDot: { color: colors.moss, fontSize: 19, fontWeight: '900', marginTop: 2 },
  foodAskText: { color: '#4B241D', fontSize: 16, lineHeight: 19, fontWeight: '900' },
  foodDemoNav: { position: 'absolute', left: 0, right: 0, bottom: 0, minHeight: 96, paddingTop: 10, paddingHorizontal: 8, paddingBottom: 14, flexDirection: 'row', justifyContent: 'space-around', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#DFE7DA' },
  foodNavItem: { flex: 1, alignItems: 'center', minHeight: 70, justifyContent: 'center' },
  foodNavGlyph: { minWidth: 44, height: 32, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center', marginBottom: 6 },
  foodNavGlyphText: { color: colors.muted, fontSize: 18, fontWeight: '900' },
  foodNavLabel: { color: colors.ink, fontSize: 14, fontWeight: '800' },
  content: { alignSelf: 'center', maxWidth: 1280, paddingHorizontal: 18, paddingBottom: 140 },
  topbar: { paddingTop: 16, paddingBottom: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  topIcon: { color: colors.ink, minWidth: 44, minHeight: 44, textAlign: 'center', lineHeight: 44, fontSize: 26 },
  capture: { color: '#FFF', backgroundColor: colors.ink, borderRadius: 99, overflow: 'hidden', paddingHorizontal: 13, paddingVertical: 8, fontWeight: '800', fontSize: 12 },
  avatar: { width: 32, height: 32, borderRadius: 16, overflow: 'hidden', textAlign: 'center', lineHeight: 32, backgroundColor: colors.ink, color: '#FFF', fontWeight: '800', fontSize: 11 },
  dashboard: { flexDirection: 'row', gap: 16, alignItems: 'stretch' },
  dashboardCompact: { flexDirection: 'column' },
  hero: { flex: 1, minHeight: 280, padding: 30, overflow: 'hidden', borderRadius: radius.lg },
  heroHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' },
  heroMeta: { color: colors.muted, fontSize: 12, fontWeight: '800' },
  heroTitle: { color: colors.ink, fontSize: 32, lineHeight: 36, fontWeight: '900', letterSpacing: -1.1, marginTop: 22, maxWidth: 680 },
  heroTitleCompact: { fontSize: 27, lineHeight: 31, maxWidth: 310 },
  heroBody: { color: colors.ink, fontSize: 15, lineHeight: 22, marginTop: 8, maxWidth: 700 },
  heroBodyCompact: { maxWidth: 310 },
  heroActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, marginTop: 20 },
  commandLane: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 22 },
  commandStep: { flexGrow: 1, flexBasis: 190, minHeight: 76, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 10 },
  commandIndex: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1 },
  commandCopy: { flex: 1, minWidth: 0 },
  commandTitle: { color: colors.ink, fontSize: 13, fontWeight: '900' },
  commandDetail: { color: colors.muted, fontSize: 11, lineHeight: 16, marginTop: 3 },
  todayRail: { width: 340, gap: 12 },
  todayRailCompact: { width: '100%' },
  segments: { backgroundColor: '#EAE9E0', padding: 4, paddingRight: 10, borderRadius: radius.pill, marginTop: 8, marginBottom: 16 },
  segmentsCompact: { maxWidth: '100%' },
  segment: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: radius.pill, minWidth: 86, alignItems: 'center' },
  segmentCompact: { minWidth: 76, paddingHorizontal: 10 },
  segmentActive: { backgroundColor: colors.paper },
  segmentText: { color: colors.muted, fontSize: 13, fontWeight: '800' },
  segmentTextActive: { color: colors.ink },
  featurePress: { flexGrow: 1 },
  featureCard: { minHeight: 124 },
  featureLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2, textTransform: 'uppercase' },
  featureTitle: { color: colors.ink, fontSize: 19, fontWeight: '900', marginTop: 18 },
  featureBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 6 },
  widgetGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  widgetPress: { flexGrow: 1, flexBasis: 240 },
  widgetCard: { minHeight: 132 },
  widgetTitle: { color: colors.ink, fontSize: 17, fontWeight: '900' },
  widgetDetail: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 8 },
  widgetRoute: { color: colors.moss, fontSize: 11, fontWeight: '900', marginTop: 14 },
  atlasHero: { marginBottom: 12, flexDirection: 'row', flexWrap: 'wrap', gap: 18, alignItems: 'stretch' },
  atlasHeroCopy: { flex: 1, minWidth: 260 },
  atlasKicker: { color: colors.moss, fontSize: 10, fontWeight: '900', letterSpacing: 1.2 },
  atlasTitle: { color: colors.ink, fontSize: 22, lineHeight: 27, fontWeight: '900', marginTop: 10 },
  atlasBody: { color: colors.muted, fontSize: 13, lineHeight: 20, marginTop: 8 },
  atlasStats: { flexBasis: 300, flexGrow: 1, flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  atlasGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  collectionStat: { flexGrow: 1, flexBasis: 130, minHeight: 82, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: 13, justifyContent: 'center' },
  collectionStatValue: { color: colors.ink, fontSize: 18, fontWeight: '900' },
  collectionStatLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 0.8, textTransform: 'uppercase', marginTop: 5 },
  collectionGroup: { flexGrow: 1, flexBasis: 360, minHeight: 210, borderRadius: radius.lg },
  collectionGroupHead: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10, marginBottom: 14 },
  collectionGroupCopy: { flex: 1, minWidth: 0 },
  collectionGroupTitle: { color: colors.ink, fontSize: 18, fontWeight: '900' },
  collectionGroupSubtitle: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 4 },
  collectionChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  collectionChip: { borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, borderRadius: radius.pill, paddingVertical: 8, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 8 },
  collectionChipGlyphText: { fontSize: 13, fontWeight: '900' },
  collectionChipName: { color: colors.ink, fontSize: 12, fontWeight: '800' },
  collectionChipCount: { color: colors.moss, fontSize: 11, fontWeight: '900' },
  manifestGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  manifestBlockPress: { flexGrow: 1, flexBasis: 300, minWidth: 240 },
  manifestBlockPressCompact: { flexBasis: 230, flexGrow: 0.8 },
  manifestBlockPressWide: { flexBasis: 460, flexGrow: 1.35 },
  manifestBlockPressFeature: { flexBasis: '100%', flexGrow: 1 },
  manifestBlock: { minHeight: 220, height: '100%', padding: 20, borderRadius: radius.lg },
  manifestBlockCompact: { minHeight: 172 },
  manifestBlockWide: { minHeight: 220 },
  manifestBlockFeature: { minHeight: 280 },
  manifestBlockTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10 },
  manifestBlockKind: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2 },
  manifestBlockTitle: { color: colors.ink, fontSize: 19, fontWeight: '900', marginTop: 18 },
  manifestBlockSubtitle: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 5 },
  manifestBlockValue: { color: colors.ink, fontSize: 15, lineHeight: 21, fontWeight: '800', marginTop: 14 },
  manifestBlockList: { marginTop: 8 },
  manifestBlockRow: { minHeight: 30, flexDirection: 'row', alignItems: 'center', gap: 7, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  manifestBlockDot: { color: colors.moss, fontSize: 16, fontWeight: '900' },
  manifestBlockRowText: { color: colors.ink, fontSize: 12, fontWeight: '800', flex: 1 },
  manifestBlockEmpty: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 8 },
  manifestBlockRoute: { color: colors.moss, fontSize: 10, fontWeight: '900', marginTop: 'auto', paddingTop: 14 },
  board: { flexDirection: 'row', gap: 12, alignItems: 'stretch' },
  boardCompact: { flexDirection: 'column' },
  column: { flex: 1, minHeight: 320, borderRadius: radius.lg },
  columnTitle: { color: colors.ink, fontSize: 18, fontWeight: '900' },
  columnSubtitle: { color: colors.muted, fontSize: 12, marginTop: 4, marginBottom: 12 },
  columnRecords: { gap: 10 },
  operatingViews: { flexDirection: 'row', gap: 12, alignItems: 'stretch', marginTop: 12 },
  operatingViewsCompact: { flexDirection: 'column' },
  operatingCard: { flex: 1, minHeight: 230 },
  operatingLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2, textTransform: 'uppercase' },
  operatingTitle: { color: colors.ink, fontSize: 18, fontWeight: '900', marginTop: 8, marginBottom: 10 },
  assemblyCard: { flex: 1.45, minHeight: 260, borderRadius: radius.lg },
  assemblyCardCompact: { flex: 0, minHeight: 0 },
  assemblyHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 10, marginBottom: 12 },
  assemblyAsk: { color: colors.moss, fontSize: 12, fontWeight: '900', paddingTop: 2 },
  assemblyTable: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, overflow: 'hidden', backgroundColor: colors.paper },
  assemblyRow: { minHeight: 70, flexDirection: 'row', alignItems: 'stretch', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  assemblyRowCompact: { flexDirection: 'column', paddingVertical: 8 },
  assemblyHeaderRow: { minHeight: 38, backgroundColor: '#F4F5EF' },
  assemblyHeader: { flex: 1, color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase', paddingHorizontal: 12, paddingVertical: 12 },
  assemblyCell: { flex: 1, minWidth: 0, paddingHorizontal: 12, paddingVertical: 10, justifyContent: 'center' },
  assemblyCellLabel: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 5 },
  assemblyDot: { width: 9, height: 9, borderRadius: 5, borderWidth: 1, borderColor: colors.line, marginBottom: 6 },
  assemblyTitle: { color: colors.ink, fontSize: 13, fontWeight: '900' },
  assemblyDetail: { color: colors.muted, fontSize: 11, lineHeight: 15, marginTop: 3 },
  assemblyEmpty: { padding: 14 },
  weekRows: { gap: 0 },
  weekRow: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  dayPill: { width: 38, height: 28, borderRadius: radius.pill, overflow: 'hidden', textAlign: 'center', lineHeight: 28, backgroundColor: colors.paper, color: colors.moss, fontSize: 11, fontWeight: '900' },
  weekCopy: { flex: 1, minWidth: 0 },
  weekTitle: { color: colors.ink, fontSize: 13, fontWeight: '900' },
  weekDetail: { color: colors.muted, fontSize: 11, marginTop: 2 },
  timelineRows: { gap: 8, marginTop: 2 },
  timelineRow: { minHeight: 42, flexDirection: 'row', alignItems: 'center', gap: 10 },
  timelineDot: { width: 14, height: 14, borderRadius: 7, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper },
  checkRows: { gap: 0 },
  checkRow: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  checkTap: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -12 },
  checkCopy: { flex: 1, minHeight: 44, justifyContent: 'center' },
  checkBox: { width: 20, height: 20, borderRadius: 6, borderWidth: 2, borderColor: colors.moss, backgroundColor: colors.paper },
  checkBoxDone: { alignItems: 'center', justifyContent: 'center' },
  checkMark: { color: colors.moss, fontSize: 12, fontWeight: '900', lineHeight: 16 },
  miniRecord: { flexGrow: 1, flexBasis: 220, borderWidth: 1, borderColor: colors.line, backgroundColor: '#FEFEFA', borderRadius: 16, padding: 12 },
  miniGlyph: { width: 34, height: 34, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginBottom: 10 },
  miniGlyphText: { fontSize: 17, fontWeight: '900' },
  miniTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  miniTitle: { color: colors.ink, fontSize: 15, fontWeight: '900', flex: 1 },
  attentionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  reviewCard: { paddingVertical: 0 },
  emptyBlock: { paddingVertical: 20 },
  emptyTitle: { color: colors.ink, fontWeight: '900', fontSize: 15, marginBottom: 5 },
  emptyBody: { color: colors.muted, fontSize: 13, lineHeight: 19 },
  viewSubtitle: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: -4, marginBottom: 12 },
  loading: { color: colors.muted, marginBottom: 12 },
  records: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, overflow: 'hidden' },
  record: { minHeight: 104, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  recordIcon: { width: 42, height: 42, borderRadius: 14, backgroundColor: '#EEEDE5', alignItems: 'center', justifyContent: 'center' },
  recordIconText: { color: colors.moss, fontSize: 14, fontWeight: '900' },
  recordCopy: { flex: 1, minWidth: 0 },
  recordTop: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'space-between' },
  recordTitle: { color: colors.ink, fontSize: 16, fontWeight: '900', flex: 1 },
  recordMeta: { color: colors.muted, fontSize: 12, marginTop: 4 },
  recordBody: { color: colors.ink, fontSize: 13, lineHeight: 18, marginTop: 7 },
  recordSource: { color: colors.moss, fontSize: 11, fontWeight: '800', marginTop: 8 },
  emptyCard: { paddingVertical: 20, alignItems: 'flex-start' },
  cardLink: { color: colors.moss, fontWeight: '900', fontSize: 13, marginTop: 14 },
  configCard: { marginTop: 20, flexDirection: 'row', flexWrap: 'wrap', gap: 14, alignItems: 'center' },
  configCopy: { flex: 1, minWidth: 240 },
  configTitle: { color: colors.ink, fontWeight: '900', fontSize: 15, marginBottom: 5 },
  configLink: { color: colors.moss, fontWeight: '900', fontSize: 12 },
  foodDebugTools: { borderWidth: 1, borderRadius: 26, padding: 16, gap: 12, marginTop: 6 },
  foodDebugHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  foodDebugPrimary: { minHeight: 42, borderRadius: radius.pill, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center' },
  disabled: { opacity: 0.36 },
  pressed: { opacity: 0.72 },
});
