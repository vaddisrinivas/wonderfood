import { Link, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { loadCatalog } from '@/src/domain/catalog';
import { getSurfaceCollectionsForLabel, queryDomainCollections } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { buildSurfaceCatalog } from '@/src/domain/surface';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { colors, radius } from '@/src/theme';

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

function pickByNeed(records: FoodRecordView[], needle: string, fallbackIndex: number) {
  return records.find((item) => `${item.collection} ${item.meta} ${item.title}`.toLowerCase().includes(needle)) ?? records[fallbackIndex];
}

function countSetting(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const FOOD_SECTIONS = ['hero', 'tabs', 'widgets', 'workspace', 'attention', 'view', 'package'] as const;
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

export default function FoodScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 32, 280) : Math.max(width - 128, 900);
  const { activeManifest } = loadCatalog();
  const settings = useLifeOSSettingsSnapshot();
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
  }, [active, db]);

  const shown = useMemo(() => {
    if (active === 'Overview') return records;
    return records.filter((item) =>
      item.collection.toLowerCase().includes(active.toLowerCase())
      || item.meta.toLowerCase().includes(active.toLowerCase())
      || item.title.toLowerCase().includes(active.toLowerCase())
    );
  }, [active, records]);

  const todayMeal = pickByNeed(records, 'meal', 0);
  const kitchenItem = pickByNeed(records, 'pantry', 1);
  const shoppingItem = pickByNeed(records, 'shopping', 2);
  const activeCopy = viewCopy[active] ?? viewCopy.Overview;
  const foodConfig = settings.runtime.surfaceConfig.food;
  const columnLimit = countSetting(foodConfig.columnLimit, 4);
  const attentionLimit = countSetting(foodConfig.attentionLimit, 3);
  const mealRecords = records.filter((item) => ['meal_plan', 'meal_log', 'recipe'].includes(item.collection)).slice(0, columnLimit);
  const kitchenRecords = records.filter((item) => ['inventory', 'ingredient', 'purchase_line'].includes(item.collection)).slice(0, columnLimit);
  const shoppingRecords = records.filter((item) => ['shopping_item', 'purchase'].includes(item.collection)).slice(0, columnLimit);
  const reviewRows = records.filter((item) => /use|planned|buy|tonight/i.test(`${item.status} ${item.meta}`)).slice(0, attentionLimit);
  const foodSections = orderedFoodSections(foodConfig.sectionOrder);
  const widgets = parseFoodWidgets(foodConfig.widgets);

  const renderFoodSection = (section: FoodSection) => {
    switch (section) {
      case 'hero':
        return foodConfig.showHero ? (
          <View key={section} style={[styles.dashboard, compact && styles.dashboardCompact]}>
            <Card tone="moss" style={styles.hero}>
              <View style={styles.heroHeader}>
                <Pill tone="moss">TONIGHT</Pill>
                <Text style={styles.heroMeta}>{loading ? 'Loading kitchen...' : `${records.length} food records`}</Text>
              </View>
              <Text style={[styles.heroTitle, compact && styles.heroTitleCompact]}>
                {todayMeal?.title ?? 'Decide dinner from what you have.'}
              </Text>
              <Text style={[styles.heroBody, compact && styles.heroBodyCompact]}>
                {todayMeal?.body || todayMeal?.meta || 'Plan, cook, shop and review receipts from one food workspace.'}
              </Text>
              <View style={styles.heroActions}>
                <ActionButton label={todayMeal ? 'Open dinner' : 'Plan dinner'} onPress={() => router.push(todayMeal ? `/record/${todayMeal.id}` : '/chat')} />
                <ActionButton label="Ask Food AI" quiet onPress={() => router.push('/chat')} />
              </View>
            </Card>

            <View style={[styles.todayRail, compact && styles.todayRailCompact]}>
              <FeatureCard tone="amber" label="Use soon" item={kitchenItem} fallbackTitle="Nothing urgent" fallbackBody="Use-soon pantry items will appear here." />
              <FeatureCard tone="blue" label="Shopping" item={shoppingItem} fallbackTitle="No shopping pressure" fallbackBody="Missing ingredients and receipt items will appear here." />
            </View>
          </View>
        ) : null;
      case 'tabs':
        return foodConfig.showViewTabs ? (
          <ScrollView key={section} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.segments}>
            {views.map((view) => (
              <Pressable
                key={view}
                accessibilityRole="button"
                accessibilityState={{ selected: active === view }}
                onPress={() => setActive(view)}
                style={[styles.segment, active === view && styles.segmentActive]}
              >
                <Text style={[styles.segmentText, active === view && styles.segmentTextActive]}>{view}</Text>
              </Pressable>
            ))}
          </ScrollView>
        ) : null;
      case 'widgets':
        return foodConfig.showWidgets && widgets.length ? (
          <View key={section}>
            <SectionTitle title="Profile widgets" action="Edit" href="/config" />
            <View style={[styles.widgetGrid, compact && styles.boardCompact]}>
              {widgets.map((widget) => (
                <Pressable key={`${widget.title}-${widget.href}`} accessibilityRole="button" onPress={() => router.push(widget.href as never)} style={({ pressed }) => [styles.widgetPress, pressed && styles.pressed]}>
                  <Card tone={widget.tone === 'neutral' ? undefined : widget.tone} style={styles.widgetCard}>
                    <Text style={styles.widgetTitle}>{widget.title}</Text>
                    <Text style={styles.widgetDetail}>{widget.detail}</Text>
                    <Text style={styles.widgetRoute}>{widget.href}</Text>
                  </Card>
                </Pressable>
              ))}
            </View>
          </View>
        ) : null;
      case 'workspace':
        return active === 'Overview' && foodConfig.showWorkspace ? (
          <View key={section}>
            <SectionTitle title="Food workspace" action="Ask" href="/chat" />
            <View style={[styles.board, compact && styles.boardCompact]}>
              <RecordColumn title="Meals" subtitle="Tonight and next plans" records={mealRecords} empty="Plan dinner from pantry." />
              <RecordColumn title="Kitchen" subtitle="Use-soon and available" records={kitchenRecords} empty="Add pantry or sync receipts." />
              <RecordColumn title="Shopping" subtitle="Missing and to-buy" records={shoppingRecords} empty="No shopping pressure." />
            </View>
          </View>
        ) : null;
      case 'attention':
        return active === 'Overview' && foodConfig.showAttention ? (
          <View key={section}>
            <SectionTitle title="Needs attention" />
            <View style={[styles.attentionGrid, compact && styles.boardCompact]}>
              {reviewRows.length ? reviewRows.map((row) => (
                <MiniRecord key={row.id} record={row} />
              )) : (
                <Card tone="moss" style={styles.emptyCard}>
                  <Text style={styles.emptyTitle}>Nothing needs review</Text>
                  <Text style={sharedStyles.muted}>Receipt matches, AI proposals and source conflicts land here before they change your kitchen.</Text>
                </Card>
              )}
            </View>
          </View>
        ) : null;
      case 'view':
        return active !== 'Overview' ? (
          <View key={section}>
            <SectionTitle title={activeCopy.title} action="Ask" href="/chat" />
            <Text style={styles.viewSubtitle}>{activeCopy.subtitle}</Text>
            {loading ? <Text style={styles.loading}>Loading food records...</Text> : null}

            <View style={styles.records}>
              {shown.length ? shown.map((record) => (
                <RecordListItem key={record.id} record={record} />
              )) : (
                <Card tone="moss" style={styles.emptyCard}>
                  <Text style={styles.emptyTitle}>{activeCopy.empty}</Text>
                  <Text style={sharedStyles.muted}>Use capture, Sources, or Food AI. No config page required.</Text>
                  <Link href="/capture" style={styles.cardLink}>Capture food →</Link>
                </Card>
              )}
            </View>
          </View>
        ) : null;
      case 'package':
        return foodConfig.showPackageCard ? (
          <Card key={section} style={styles.configCard}>
            <View style={styles.configCopy}>
              <Text style={styles.configTitle}>Food is the active package</Text>
              <Text style={sharedStyles.muted}>Views come from config, but the workspace stays about dinner, pantry and shopping.</Text>
            </View>
            <Link href="/config" style={styles.configLink}>Edit package</Link>
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
              <Text style={styles.brand}>LIFEOS / FOOD</Text>
              <Text style={styles.date}>Kitchen, meals, recipes, shopping</Text>
            </View>
            <View style={styles.topActions}>
              <Link href="/search" style={styles.topIcon}>⌕</Link>
              <Link href="/capture" style={styles.capture}>＋ Add</Link>
              <Link href="/settings" style={styles.avatar}>SV</Link>
            </View>
          </View>

          {foodSections.map(renderFoodSection)}
        </View>
      </ScrollView>
    </Page>
  );
}

function RecordColumn({ title, subtitle, records, empty }: {
  title: string;
  subtitle: string;
  records: FoodRecordView[];
  empty: string;
}) {
  return (
    <Card style={styles.column}>
      <Text style={styles.columnTitle}>{title}</Text>
      <Text style={styles.columnSubtitle}>{subtitle}</Text>
      <View style={styles.columnRecords}>
        {records.length ? records.map((record) => <MiniRecord key={record.id} record={record} />) : (
          <Text style={styles.emptyBody}>{empty}</Text>
        )}
      </View>
    </Card>
  );
}

function MiniRecord({ record }: { record: FoodRecordView }) {
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.miniRecord, pressed && styles.pressed]}>
        <View style={styles.miniTop}>
          <Text style={styles.miniTitle} numberOfLines={1}>{record.title}</Text>
          <Pill tone={record.tone}>{record.status}</Pill>
        </View>
        <Text style={styles.recordMeta} numberOfLines={1}>{record.meta}</Text>
        <Text style={styles.recordBody} numberOfLines={2}>{record.body}</Text>
      </Pressable>
    </Link>
  );
}

function RecordListItem({ record }: { record: FoodRecordView }) {
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable style={({ pressed }) => [styles.record, pressed && styles.pressed]}>
        <View style={styles.recordIcon}><Text style={styles.recordIconText}>{record.collection.slice(0, 1).toUpperCase()}</Text></View>
        <View style={styles.recordCopy}>
          <View style={styles.recordTop}>
            <Text style={styles.recordTitle}>{record.title}</Text>
            <Pill tone={record.tone}>{record.status}</Pill>
          </View>
          <Text style={styles.recordMeta}>{record.meta}</Text>
          <Text style={styles.recordBody} numberOfLines={2}>{record.body}</Text>
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
  return (
    <Link href={item ? { pathname: '/record/[id]', params: { id: item.id } } : '/capture'} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.featurePress, pressed && styles.pressed]}>
        <Card tone={tone} style={styles.featureCard}>
          <Text style={styles.featureLabel}>{label}</Text>
          <Text style={styles.featureTitle}>{item?.title ?? fallbackTitle}</Text>
          <Text style={styles.featureBody}>{item?.meta || item?.body || fallbackBody}</Text>
        </Card>
      </Pressable>
    </Link>
  );
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
  board: { flexDirection: 'row', gap: 12, alignItems: 'stretch' },
  boardCompact: { flexDirection: 'column' },
  column: { flex: 1, minHeight: 320 },
  columnTitle: { color: colors.ink, fontSize: 18, fontWeight: '900' },
  columnSubtitle: { color: colors.muted, fontSize: 12, marginTop: 4, marginBottom: 12 },
  columnRecords: { gap: 10 },
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
