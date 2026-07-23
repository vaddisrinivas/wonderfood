import { Link, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { loadCatalog } from '@/src/domain/catalog';
import { getSurfaceCollectionsForLabel, queryDomainCollections } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { buildSurfaceCatalog } from '@/src/domain/surface';
import { useLifeOSDatabase } from '@/src/db/provider';
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

export default function FoodScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 36, 280) : '100%';
  const { activeManifest } = loadCatalog();
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
  const reviewRows = records.slice(0, 2);

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

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.segments}>
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

          {active === 'Overview' ? (
            <>
              <SectionTitle title="Food today" />
              <View style={sharedStyles.grid}>
                <FeatureCard tone="amber" label="Use soon" item={kitchenItem} fallbackTitle="Nothing urgent" fallbackBody="Use-soon pantry items will appear here." />
                <FeatureCard tone="blue" label="Shopping" item={shoppingItem} fallbackTitle="No shopping pressure" fallbackBody="Missing ingredients and receipt items will appear here." />
              </View>

              <SectionTitle title="Review before writing" />
              <Card style={styles.reviewCard}>
                {reviewRows.length ? reviewRows.map((row) => (
                  <Row
                    key={row.id}
                    icon="!"
                    title={row.title}
                    detail={row.meta || `${row.collection} · needs review`}
                    href={{ pathname: '/record/[id]', params: { id: row.id } }}
                  />
                )) : (
                  <View style={styles.emptyBlock}>
                    <Text style={styles.emptyTitle}>No food reviews pending</Text>
                    <Text style={styles.emptyBody}>Receipt matches, AI proposals and source conflicts land here before they change your kitchen.</Text>
                  </View>
                )}
              </Card>
            </>
          ) : null}

          <SectionTitle title={activeCopy.title} action="Ask" href="/chat" />
          <Text style={styles.viewSubtitle}>{activeCopy.subtitle}</Text>
          {loading ? <Text style={styles.loading}>Loading food records...</Text> : null}

          <View style={styles.records}>
            {shown.length ? shown.map((record) => (
              <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild key={record.id}>
                <Pressable style={({ pressed }) => [styles.record, pressed && styles.pressed]}>
                  <View style={styles.recordIcon}><Text style={styles.recordIconText}>{record.collection.slice(0, 1).toUpperCase()}</Text></View>
                  <View style={styles.recordCopy}>
                    <View style={styles.recordTop}>
                      <Text style={styles.recordTitle}>{record.title}</Text>
                      <Pill tone={record.tone}>{record.status}</Pill>
                    </View>
                    <Text style={styles.recordMeta}>{record.meta}</Text>
                    <Text style={styles.recordBody} numberOfLines={2}>{record.body}</Text>
                    <Text style={styles.recordSource}>{record.source}</Text>
                  </View>
                </Pressable>
              </Link>
            )) : (
              <Card tone="moss" style={styles.emptyCard}>
                <Text style={styles.emptyTitle}>{activeCopy.empty}</Text>
                <Text style={sharedStyles.muted}>Use capture, Sources, or Food AI. No config page required.</Text>
                <Link href="/capture" style={styles.cardLink}>Capture food →</Link>
              </Card>
            )}
          </View>

          <Card style={styles.configCard}>
            <View style={styles.configCopy}>
              <Text style={styles.configTitle}>Food is the active package</Text>
              <Text style={sharedStyles.muted}>Views come from config, but the workspace stays about dinner, pantry and shopping.</Text>
            </View>
            <Link href="/config" style={styles.configLink}>Edit package</Link>
          </Card>
        </View>
      </ScrollView>
    </Page>
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
  content: { alignSelf: 'center', maxWidth: 1080, paddingBottom: 44 },
  topbar: { paddingTop: 16, paddingBottom: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  topIcon: { color: colors.ink, fontSize: 26 },
  capture: { color: '#FFF', backgroundColor: colors.ink, borderRadius: 99, overflow: 'hidden', paddingHorizontal: 13, paddingVertical: 8, fontWeight: '800', fontSize: 12 },
  avatar: { width: 32, height: 32, borderRadius: 16, overflow: 'hidden', textAlign: 'center', lineHeight: 32, backgroundColor: colors.ink, color: '#FFF', fontWeight: '800', fontSize: 11 },
  hero: { minHeight: 236, padding: 22, overflow: 'hidden' },
  heroHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' },
  heroMeta: { color: colors.muted, fontSize: 12, fontWeight: '800' },
  heroTitle: { color: colors.ink, fontSize: 32, lineHeight: 36, fontWeight: '900', letterSpacing: -1.1, marginTop: 22, maxWidth: 680 },
  heroTitleCompact: { fontSize: 27, lineHeight: 31, maxWidth: 310 },
  heroBody: { color: colors.ink, fontSize: 15, lineHeight: 22, marginTop: 8, maxWidth: 700 },
  heroBodyCompact: { maxWidth: 310 },
  heroActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, marginTop: 20 },
  segments: { backgroundColor: '#EAE9E0', padding: 4, borderRadius: radius.pill, marginTop: 18, marginBottom: 20 },
  segment: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: radius.pill },
  segmentActive: { backgroundColor: colors.paper },
  segmentText: { color: colors.muted, fontSize: 13, fontWeight: '800' },
  segmentTextActive: { color: colors.ink },
  featurePress: { flexGrow: 1, flexBasis: 260 },
  featureCard: { minHeight: 148 },
  featureLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2, textTransform: 'uppercase' },
  featureTitle: { color: colors.ink, fontSize: 19, fontWeight: '900', marginTop: 18 },
  featureBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 6 },
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
