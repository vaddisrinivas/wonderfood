import { Link } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, Page, PageHeader, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { useLifeOSDatabase } from '@/src/db/provider';
import { getSurfaceCollectionsForLabel, queryDomainCollections } from '@/src/domain/queries';
import { colors, radius } from '@/src/theme';
import { loadCatalog } from '@/src/domain/catalog';
import { buildSurfaceCatalog } from '@/src/domain/surface';

type FoodRecordView = {
  id: string;
  collection?: string;
  title: string;
  meta: string;
  status: string;
  tone: 'moss' | 'blue' | 'amber' | 'plum' | 'neutral';
  body: string;
  source: string;
};

const { activeManifest } = loadCatalog();
const surfaceCatalog = buildSurfaceCatalog(activeManifest);

const defaultTab = surfaceCatalog.tabs[0] ?? 'Overview';
const defaultViews = surfaceCatalog.tabs;

export default function FoodScreen() {
  const db = useLifeOSDatabase();
  const [active, setActive] = useState(defaultTab);
  const [records, setRecords] = useState<FoodRecordView[]>([]);
  const [loading, setLoading] = useState(true);

  const shown: FoodRecordView[] = useMemo(() => {
    if (active === 'Overview') {
      return records;
    }
    return records.filter((item) => item.collection?.toLowerCase().includes(active.toLowerCase()) || item.meta.toLowerCase().includes(active.toLowerCase()));
  }, [active, records]);

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

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.topbar}>
            <View><Text style={styles.brand}>LIFEOS / FOOD</Text><Text style={styles.date}>Package-driven workspace</Text></View>
            <View style={styles.topActions}>
              <Link href="/search" style={styles.topIcon}>⌕</Link>
              <Link href="/capture" style={styles.capture}>＋ Add</Link>
              <Link href="/system" style={styles.avatar}>SV</Link>
            </View>
          </View>
          <PageHeader eyebrow="Food domain · Active" title="Eat well. Waste less." subtitle="Meals, recipes, pantry, shopping and nutrition share one connected graph." />

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.segments}>
            {defaultViews.map((view) => (
              <Pressable key={view} accessibilityRole="button" accessibilityState={{ selected: active === view }} onPress={() => setActive(view)} style={[styles.segment, active === view && styles.segmentActive]}>
                <Text style={[styles.segmentText, active === view && styles.segmentTextActive]}>{view}</Text>
              </Pressable>
            ))}
          </ScrollView>

          {active === 'Overview' ? (
            <>
              <View style={sharedStyles.grid}>
                <Card tone="moss" style={styles.feature}><Text style={styles.kicker}>TONIGHT</Text><Text style={styles.featureTitle}>Tandoori chicken</Text><Text style={sharedStyles.muted}>35 min · ingredients ready</Text></Card>
                <Card tone="amber" style={styles.feature}><Text style={styles.kicker}>USE SOON</Text><Text style={styles.featureTitle}>2 pantry items</Text><Text style={sharedStyles.muted}>Yogurt and coriander</Text></Card>
                <Card tone="blue" style={styles.feature}><Text style={styles.kicker}>SHOPPING</Text><Text style={styles.featureTitle}>8 open items</Text><Text style={sharedStyles.muted}>Grouped by store aisle</Text></Card>
              </View>
            </>
          ) : null}

          <SectionTitle title={active === 'Overview' ? 'Your food graph' : active} action="Ask AI" href="/chat" />
          {loading ? <Text style={styles.loading}>Loading data…</Text> : null}
          <View style={styles.records}>
            {shown.map((record) => (
              <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild key={record.id}>
                <Pressable style={({ pressed }) => [styles.record, pressed && { opacity: 0.65 }]}> 
                  <View style={styles.recordCopy}>
                    <View style={styles.recordTop}><Text style={styles.recordTitle}>{record.title}</Text><Pill tone={record.tone}>{record.status}</Pill></View>
                    <Text style={styles.recordMeta}>{record.meta}</Text>
                    <Text style={styles.recordBody} numberOfLines={2}>{record.body}</Text>
                    <Text style={styles.recordSource}>{record.source}</Text>
                  </View>
                  <Text style={styles.chevron}>›</Text>
                </Pressable>
              </Link>
            ))}
          </View>
          <Card style={styles.schemaCard}>
            <View style={{ flex: 1 }}><Text style={styles.schemaTitle}>This workspace comes from config</Text><Text style={sharedStyles.muted}>Food schemas, views, skills and agent tools can be replaced by another domain package.</Text></View>
            <Link href="/system" style={styles.schemaLink}>Inspect system →</Link>
          </Card>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  topbar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  topIcon: { color: colors.ink, fontSize: 27 },
  capture: { color: '#FFF', backgroundColor: colors.ink, borderRadius: 99, paddingHorizontal: 13, paddingVertical: 8, fontWeight: '800', fontSize: 12 },
  avatar: { width: 32, height: 32, borderRadius: 16, overflow: 'hidden', textAlign: 'center', lineHeight: 32, backgroundColor: colors.ink, color: '#FFF', fontWeight: '800', fontSize: 11 },
  segments: { backgroundColor: '#EAE9E0', padding: 4, borderRadius: radius.pill, marginBottom: 20 },
  segment: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: radius.pill },
  segmentActive: { backgroundColor: colors.paper },
  segmentText: { color: colors.muted, fontSize: 13, fontWeight: '700' },
  segmentTextActive: { color: colors.ink },
  feature: { minWidth: 0, flexGrow: 1, flexBasis: 220, minHeight: 128 },
  kicker: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1 },
  featureTitle: { color: colors.ink, fontSize: 20, fontWeight: '800', marginTop: 20, marginBottom: 5 },
  records: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, overflow: 'hidden' },
  record: { minHeight: 126, padding: 16, flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  recordCopy: { flex: 1, minWidth: 0 },
  recordTop: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'space-between' },
  recordTitle: { color: colors.ink, fontSize: 17, fontWeight: '800', flex: 1 },
  recordMeta: { color: colors.muted, fontSize: 12, marginTop: 4 },
  recordBody: { color: colors.ink, fontSize: 13, lineHeight: 19, marginTop: 9 },
  recordSource: { color: colors.moss, fontSize: 11, fontWeight: '700', marginTop: 9 },
  loading: { color: colors.muted, marginBottom: 12 },
  chevron: { color: colors.muted, fontSize: 28, paddingLeft: 12 },
  schemaCard: { marginTop: 20, flexDirection: 'row', flexWrap: 'wrap', gap: 18, alignItems: 'center' },
  schemaTitle: { color: colors.ink, fontWeight: '800', fontSize: 15, marginBottom: 5 },
  schemaLink: { color: colors.moss, fontWeight: '800', fontSize: 12 },
});
