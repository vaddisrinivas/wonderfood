import { Link, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { DashboardBlock, loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { getDomainRecordCanonical, getSurfaceCollectionsForLabel, queryDomainCollections } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { buildSurfaceCatalog } from '@/src/domain/surface';
import { useLifeOSDatabase } from '@/src/db/provider';
import { upsertRecord } from '@/src/db/records';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';

type FoodRecordView = DomainRecordViewModel;

const viewCopy: Record<string, { title: string; subtitle: string; empty: string }> = {
  Overview: {
    title: 'Food command center',
    subtitle: 'Tonight, use-soon food, review queue and recent kitchen changes.',
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
    empty: 'Add pantry items or sync receipts to build the kitchen.',
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
    empty: `Connect sources or add a ${domainLabel.toLowerCase()} item to wake up this space.`,
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

const FOOD_SECTIONS = ['hero', 'tabs', 'manifest', 'widgets', 'workspace', 'attention', 'view', 'package'] as const;
type FoodSection = typeof FOOD_SECTIONS[number];
type FoodWidget = { title: string; detail: string; tone: 'moss' | 'blue' | 'amber' | 'plum' | 'neutral'; href: string };

function orderedFoodSections(value: string) {
  const allowed = new Set<string>(FOOD_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is FoodSection => allowed.has(section));
  const missing = FOOD_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

function toWidgetTone(value: string): FoodWidget['tone'] {
  return value === 'moss' || value === 'blue' || value === 'amber' || value === 'plum' || value === 'neutral' ? value : 'neutral';
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
      ] = line.split('|').map((part) => part.trim());
      const parsedLimit = Number.parseInt(limit, 10);
      return {
        id: id || `${domainId}:custom-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`,
        surface: `${domainId}.overview`,
        title: title || 'Custom dashboard block',
        subtitle: subtitle || 'Configured from the app profile.',
        kind: kind === 'spotlight' || kind === 'metric' || kind === 'action' ? kind : 'list',
        tone: tone === 'moss' || tone === 'amber' || tone === 'plum' || tone === 'blue' ? tone : 'neutral',
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

export default function FoodScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 32, 280) : Math.max(width - 128, 900);
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeDomainId, activeManifest } = loadCatalog();
  const domainLabel = activeManifest.label;
  const isFoodDomain = activeDomainId === 'food';
  const theme = useLifeOSTheme();
  const surfaceCatalog = useMemo(() => buildSurfaceCatalog(activeManifest), [activeManifest]);
  const views = surfaceCatalog.tabs;
  const defaultTab = views[0] ?? 'Overview';
  const db = useLifeOSDatabase();
  const [active, setActive] = useState(defaultTab);
  const [records, setRecords] = useState<FoodRecordView[]>([]);
  const [loading, setLoading] = useState(true);

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
  }, [active, activeDomainId, db]);

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
                    <CommandStep index="04" title="Prep" detail="Ask, cook, log, update" tone="plum" href="/chat" />
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
          <ScrollView key={section} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.segments, { backgroundColor: theme.colors.canvas }]}>
            {views.map((view) => (
              <Pressable
                key={view}
                accessibilityRole="button"
                accessibilityState={{ selected: active === view }}
                onPress={() => setActive(view)}
                style={[styles.segment, active === view && styles.segmentActive, active === view && { backgroundColor: theme.colors.paper }]}
              >
                <Text style={[styles.segmentText, { color: active === view ? theme.colors.ink : theme.colors.muted }]}>{view}</Text>
              </Pressable>
            ))}
          </ScrollView>
        ) : null;
      case 'widgets':
        return foodConfig.showWidgets && widgets.length ? (
          <View key={section}>
            <SectionTitle title="Shortcuts" action="Edit" href="/config" />
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
            <SectionTitle title={`${domainLabel} dashboard`} action="Tune" href="/config" />
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
      case 'workspace':
        return active === 'Overview' && foodConfig.showWorkspace ? (
          <View key={section}>
              <SectionTitle title={`${domainLabel} workspace`} action="Ask" href="/chat" />
            <View style={[styles.board, compact && styles.boardCompact]}>
              {isFoodDomain ? (
                <>
                  <RecordColumn title="Meals" subtitle="Tonight and next plans" records={mealRecords} empty="Plan dinner from pantry." />
                  <RecordColumn title="Kitchen" subtitle="Use-soon and available" records={kitchenRecords} empty="Add pantry or sync receipts." />
                  <RecordColumn title="Shopping" subtitle="Missing and to-buy" records={shoppingRecords} empty="No shopping pressure." />
                </>
              ) : surfaceColumns.map((surface) => (
                <RecordColumn key={surface.id} title={surface.title} subtitle={surface.subtitle} records={surface.records} empty={surface.empty} />
              ))}
            </View>
            {isFoodDomain ? <FoodOperatingViews meals={mealRecords} kitchen={kitchenRecords} shopping={shoppingRecords} compact={compact} onToggleShopping={toggleShoppingRecord} /> : null}
          </View>
        ) : null;
      case 'attention':
        return active === 'Overview' && foodConfig.showAttention ? (
          <View key={section}>
            <SectionTitle title="Review before writing" />
            <View style={[styles.attentionGrid, compact && styles.boardCompact]}>
              {reviewRows.length ? reviewRows.map((row) => (
                <MiniRecord key={row.id} record={row} />
              )) : (
                <Card tone="moss" style={styles.emptyCard}>
                  <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>Nothing needs review</Text>
                  <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>AI suggestions, source conflicts and reviewable changes land here before they change your {domainLabel.toLowerCase()} graph.</Text>
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
                <RecordListItem key={record.id} record={record} />
              )) : (
                <Card tone="moss" style={styles.emptyCard}>
                  <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>{activeCopy.empty}</Text>
                  <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>{`Use capture, Sources, or ${domainLabel} AI. No code change required.`}</Text>
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
              <Text style={[styles.configTitle, { color: theme.colors.ink }]}>{domainLabel} can be customized</Text>
              <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>Views, cards, source rules and assistant behavior are editable from Settings.</Text>
            </View>
            <Link href="/config" style={[styles.configLink, { color: theme.colors.moss }]}>Tune</Link>
          </Card>
        ) : null;
      default:
        return null;
    }
  };

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
  const metricValue = block.kind === 'metric'
    ? records.length
      ? records.map((record) => record.title).slice(0, 2).join(' + ')
      : 'No records yet'
    : primary?.title ?? (block.kind === 'action' ? 'Open workspace' : 'Awaiting data');
  return (
    <Link href={href as never} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.manifestBlockPress, pressed && styles.pressed]}>
        <Card tone={block.tone === 'neutral' ? undefined : block.tone} style={styles.manifestBlock}>
          <View style={styles.manifestBlockTop}>
            <Text style={[styles.manifestBlockKind, { color: theme.colors.muted }]}>{block.kind.toUpperCase()}</Text>
            <Pill tone={block.tone}>{records.length ? `${records.length} hit${records.length === 1 ? '' : 's'}` : 'config'}</Pill>
          </View>
          <Text style={[styles.manifestBlockTitle, { color: theme.colors.ink }]}>{block.title}</Text>
          <Text style={[styles.manifestBlockSubtitle, { color: theme.colors.muted }]}>{block.subtitle}</Text>
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

function RecordColumn({ title, subtitle, records, empty }: {
  title: string;
  subtitle: string;
  records: FoodRecordView[];
  empty: string;
}) {
  const theme = useLifeOSTheme();
  return (
    <Card style={styles.column}>
      <Text style={[styles.columnTitle, { color: theme.colors.ink }]}>{title}</Text>
      <Text style={[styles.columnSubtitle, { color: theme.colors.muted }]}>{subtitle}</Text>
      <View style={styles.columnRecords}>
        {records.length ? records.map((record) => <MiniRecord key={record.id} record={record} />) : (
          <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>{empty}</Text>
        )}
      </View>
    </Card>
  );
}

function FoodOperatingViews({ meals, kitchen, shopping, compact, onToggleShopping }: {
  meals: FoodRecordView[];
  kitchen: FoodRecordView[];
  shopping: FoodRecordView[];
  compact: boolean;
  onToggleShopping: (record: FoodRecordView) => void;
}) {
  return (
    <View style={[styles.operatingViews, compact && styles.operatingViewsCompact]}>
      <MealWeekPlan records={meals} />
      <PantryTimeline records={kitchen} />
      <ShoppingChecklist records={shopping} onToggle={onToggleShopping} />
    </View>
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
            <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: /in cart|bought/i.test(record.status) }} onPress={() => onToggle(record)} style={({ pressed }) => [styles.checkTap, pressed && styles.pressed]}>
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

function MiniRecord({ record }: { record: FoodRecordView }) {
  const theme = useLifeOSTheme();
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.miniRecord, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
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

function RecordListItem({ record }: { record: FoodRecordView }) {
  const theme = useLifeOSTheme();
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable style={({ pressed }) => [styles.record, { borderBottomColor: theme.colors.line }, pressed && styles.pressed]}>
        <View style={[styles.recordIcon, { backgroundColor: theme.colors.canvas }]}><Text style={[styles.recordIconText, { color: theme.colors.moss }]}>{record.collection.slice(0, 1).toUpperCase()}</Text></View>
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
  content: { alignSelf: 'center', maxWidth: 1480, paddingBottom: 140 },
  topbar: { paddingTop: 16, paddingBottom: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  topIcon: { color: colors.ink, fontSize: 26 },
  capture: { color: '#FFF', backgroundColor: colors.ink, borderRadius: 99, overflow: 'hidden', paddingHorizontal: 13, paddingVertical: 8, fontWeight: '800', fontSize: 12 },
  avatar: { width: 32, height: 32, borderRadius: 16, overflow: 'hidden', textAlign: 'center', lineHeight: 32, backgroundColor: colors.ink, color: '#FFF', fontWeight: '800', fontSize: 11 },
  dashboard: { flexDirection: 'row', gap: 16, alignItems: 'stretch' },
  dashboardCompact: { flexDirection: 'column' },
  hero: { flex: 1, minHeight: 260, padding: 28, overflow: 'hidden' },
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
  todayRail: { width: 320, gap: 12 },
  todayRailCompact: { width: '100%' },
  segments: { backgroundColor: '#EAE9E0', padding: 4, borderRadius: radius.pill, marginTop: 18, marginBottom: 20 },
  segment: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: radius.pill },
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
  manifestGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  manifestBlockPress: { flexGrow: 1, flexBasis: 260 },
  manifestBlock: { minHeight: 188, padding: 18 },
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
  column: { flex: 1, minHeight: 320 },
  columnTitle: { color: colors.ink, fontSize: 18, fontWeight: '900' },
  columnSubtitle: { color: colors.muted, fontSize: 12, marginTop: 4, marginBottom: 12 },
  columnRecords: { gap: 10 },
  operatingViews: { flexDirection: 'row', gap: 12, alignItems: 'stretch', marginTop: 12 },
  operatingViewsCompact: { flexDirection: 'column' },
  operatingCard: { flex: 1, minHeight: 230 },
  operatingLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2, textTransform: 'uppercase' },
  operatingTitle: { color: colors.ink, fontSize: 18, fontWeight: '900', marginTop: 8, marginBottom: 10 },
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
  pressed: { opacity: 0.72 },
});
