import { Link } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, Page, PageHeader, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { useLifeOSDatabase } from '@/src/db/provider';
import { getSurfaceCollectionsForLabel, queryDomainCollections } from '@/src/domain/queries';
import { colors, radius } from '@/src/theme';
import { loadCatalog } from '@/src/domain/catalog';
import { buildSurfaceCatalog } from '@/src/domain/surface';
import { DomainRecordViewModel } from '@/src/domain/renderer';

type FoodRecordView = DomainRecordViewModel;

function getTopSummary(records: FoodRecordView[]) {
  return records.slice(0, 3);
}

export default function FoodScreen() {
  const { activeManifest } = loadCatalog();
  const surfaceCatalog = useMemo(() => buildSurfaceCatalog(activeManifest), [activeManifest]);
  const defaultViews = surfaceCatalog.tabs;
  const defaultTab = defaultViews[0] ?? 'Overview';
  const db = useLifeOSDatabase();
  const [active, setActive] = useState(defaultTab);
  const [records, setRecords] = useState<FoodRecordView[]>([]);
  const [loading, setLoading] = useState(true);

  const shown: FoodRecordView[] = useMemo(() => {
    if (active === 'Overview') {
      return records;
    }
    return records.filter((item) =>
      item.collection.toLowerCase().includes(active.toLowerCase())
      || item.meta.toLowerCase().includes(active.toLowerCase())
    );
  }, [active, records]);

  const hasRecords = records.length > 0;
  const topSummary = getTopSummary(records);

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
            <View><Text style={styles.brand}>LIFEOS / {activeManifest.label.toUpperCase()}</Text><Text style={styles.date}>Package-driven workspace</Text></View>
            <View style={styles.topActions}>
              <Link href="/search" style={styles.topIcon}>⌕</Link>
              <Link href="/capture" style={styles.capture}>＋ Add</Link>
              <Link href="/system" style={styles.avatar}>SV</Link>
            </View>
          </View>
          <PageHeader
            eyebrow={`${activeManifest.label} domain · Active`}
            title={`${activeManifest.label} workspace`}
            subtitle={`${activeManifest.collections.length} collections, ${activeManifest.relations.length} relations, and ${activeManifest.skills.length} skills run from one canonical graph.`}
          />

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.segments}>
            {defaultViews.map((view) => (
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
            <View style={sharedStyles.grid}>
              {hasRecords ? (
                topSummary.map((item, index) => {
                  const labels = defaultViews.slice(1, 4).map((label) => label.toUpperCase());
                  const tones: Array<'moss' | 'amber' | 'blue'> = ['moss', 'amber', 'blue'];
                  if (!item) {
                    return null;
                  }

                  return (
                    <Card key={`${item.id}-${index}`} tone={tones[index] ?? 'moss'} style={styles.feature}>
                      <Text style={styles.kicker}>{labels[index] ?? 'ACTIVE'}</Text>
                      <Text style={styles.featureTitle}>{item.title}</Text>
                      <Text style={sharedStyles.muted}>{item.meta}</Text>
                    </Card>
                  );
                })
              ) : (
                <Card tone="moss" style={styles.feature}>
                  <Text style={styles.kicker}>NO ROWS</Text>
                  <Text style={styles.featureTitle}>Add your first {activeManifest.label} record</Text>
                  <Text style={sharedStyles.muted}>Use capture or sync so this workspace can render live rows.</Text>
                  <Link href="/capture" style={styles.cardLink}>Create first record →</Link>
                </Card>
              )}
            </View>
          ) : null}

          <SectionTitle title={active === 'Overview' ? 'Your active graph' : active} action="Ask AI" href="/chat" />
          {loading ? <Text style={styles.loading}>Loading data…</Text> : null}
          <View style={styles.records}>
            {shown.length ? (
              shown.map((record) => (
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
              ))
            ) : (
              <Card tone="moss" style={styles.emptyCard}>
                <Text style={styles.emptyTitle}>No {activeManifest.label.toLowerCase()} rows yet</Text>
                <Text style={sharedStyles.muted}>Try capture or connect sources in System.</Text>
              </Card>
            )}
          </View>
          <Card style={styles.schemaCard}>
            <View style={{ flex: 1 }}>
              <Text style={styles.schemaTitle}>This workspace comes from config</Text>
              <Text style={sharedStyles.muted}>
                {activeManifest.label} schemas, views, skills, and tools can be replaced by another domain package.
              </Text>
            </View>
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
  emptyCard: { paddingVertical: 20, alignItems: 'flex-start' },
  emptyTitle: { color: colors.ink, fontWeight: '800', fontSize: 15, marginBottom: 4 },
  schemaCard: { marginTop: 20, flexDirection: 'row', flexWrap: 'wrap', gap: 18, alignItems: 'center' },
  schemaTitle: { color: colors.ink, fontWeight: '800', fontSize: 15, marginBottom: 5 },
  schemaLink: { color: colors.moss, fontWeight: '800', fontSize: 12 },
  cardLink: { color: colors.moss, fontWeight: '800', fontSize: 13, marginTop: 14 },
});
