import { Link, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { queryDomainRecords } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';

function humanizeCollection(collection: string) {
  return collection
    .split('_')
    .map((part) => part ? `${part[0].toUpperCase()}${part.slice(1)}` : part)
    .join(' ');
}

function toneForIndex(index: number): 'moss' | 'amber' | 'blue' | 'plum' {
  return index % 4 === 0 ? 'moss' : index % 4 === 1 ? 'amber' : index % 4 === 2 ? 'blue' : 'plum';
}

function relationLabel(name: string) {
  return name.replaceAll('_', ' ');
}

export default function CollectionScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const theme = useLifeOSTheme();
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeDomainId, activeManifest } = loadCatalog();
  const db = useLifeOSDatabase();
  const collectionId = String(id ?? '');
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      const all = await queryDomainRecords(db);
      if (!cancelled) {
        setRecords(all);
        setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [db, activeDomainId]);

  const exists = activeManifest.collections.includes(collectionId);
  const collectionRecords = records.filter((record) => record.collection === collectionId);
  const incoming = activeManifest.relations.filter((relation) => relation.to === collectionId || relation.to === '*');
  const outgoing = activeManifest.relations.filter((relation) => relation.from === collectionId);
  const surfaces = activeManifest.surfaces.filter((surface) => surface.collections.includes(collectionId));
  const sourceCounts = useMemo(() => collectionRecords.reduce<Record<string, number>>((counts, record) => {
    const source = record.source.split(' · ')[0] || 'local';
    counts[source] = (counts[source] ?? 0) + 1;
    return counts;
  }, {}), [collectionRecords]);
  const reviewRecords = collectionRecords.filter((record) => /review|need|buy|planned|use|open|tonight/i.test(`${record.status} ${record.meta} ${record.body}`));
  const collectionTitle = humanizeCollection(collectionId || 'collection');

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.contextBar}>
            <View>
              <Text style={[styles.brand, { color: theme.colors.moss }]}>{`LIFEOS / ${activeManifest.label.toUpperCase()} COLLECTION`}</Text>
              <Text style={[styles.context, { color: theme.colors.muted }]}>{activeManifest.label} data plane · {activeManifest.data_homes.join(', ')}</Text>
            </View>
            <Pill tone={exists ? 'moss' : 'amber'}>{exists ? 'In schema' : 'Unknown'}</Pill>
          </View>

          <Card tone={exists ? 'moss' : 'amber'} style={styles.hero}>
            <View style={styles.heroTop}>
              <View style={styles.heroCopy}>
                <Text style={[styles.kicker, { color: theme.colors.moss }]}>COLLECTION</Text>
                <Text style={[styles.title, { color: theme.colors.ink }]}>{collectionTitle}</Text>
                <Text style={[styles.subtitle, { color: theme.colors.muted }]}>
                  {exists
                    ? `${collectionRecords.length} loaded records. ${incoming.length + outgoing.length} schema relations. ${surfaces.length ? `Visible in ${surfaces.map((surface) => surface.label).join(', ')}.` : 'Support collection for the active graph.'}`
                    : 'This collection is not in the active domain manifest.'}
                </Text>
              </View>
              <View style={styles.heroActions}>
                <ActionButton label="Add record" onPress={() => router.push({ pathname: '/capture', params: { collection: collectionId } })} />
                <ActionButton label="Ask with collection" quiet onPress={() => router.push('/chat')} />
              </View>
            </View>
            <View style={[styles.statGrid, compact && styles.stack]}>
              <Stat label="Records" value={String(collectionRecords.length)} tone="moss" />
              <Stat label="Review" value={String(reviewRecords.length)} tone={reviewRecords.length ? 'amber' : 'moss'} />
              <Stat label="Relations" value={String(incoming.length + outgoing.length)} tone="blue" />
              <Stat label="Sources" value={String(Object.keys(sourceCounts).length)} tone="plum" />
            </View>
          </Card>

          {!exists ? (
            <Card style={styles.emptyCard}>
              <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>Collection not found</Text>
              <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>Switch domains or edit the domain package in Config Studio.</Text>
              <Link href="/config" style={[styles.link, { color: theme.colors.moss }]}>Open Config Studio →</Link>
            </Card>
          ) : null}

          {exists ? (
            <>
              <SectionTitle title="Records" action="Search" href="/search" />
              <Card style={styles.recordsCard}>
                {loading ? <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>Loading records…</Text> : null}
                {!loading && collectionRecords.length ? collectionRecords.map((record) => (
                  <RecordRow key={record.id} record={record} />
                )) : null}
                {!loading && !collectionRecords.length ? (
                  <View style={styles.emptyState}>
                    <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>No {collectionTitle.toLowerCase()} records yet</Text>
                    <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>Capture one, pull Notion/Sheets, or let Chat draft a reviewable record.</Text>
                    <View style={styles.emptyActions}>
                      <ActionButton label="Capture" onPress={() => router.push({ pathname: '/capture', params: { collection: collectionId } })} />
                      <ActionButton label="Open sources" quiet onPress={() => router.push('/sources')} />
                    </View>
                  </View>
                ) : null}
              </Card>

              <View style={[styles.twoColumn, compact && styles.stack]}>
                <View style={styles.column}>
                  <SectionTitle title="Schema relations" action="Config" href="/config" />
                  <Card style={styles.relationCard}>
                    {[...outgoing, ...incoming].length ? [...outgoing, ...incoming].map((relation, index) => (
                      <View key={`${relation.from}-${relation.name}-${relation.to}-${index}`} style={[styles.relationRow, { borderBottomColor: theme.colors.line }]}>
                        <Pill tone={toneForIndex(index)}>{relation.from === collectionId ? 'out' : 'in'}</Pill>
                        <View style={styles.relationCopy}>
                          <Text style={[styles.relationTitle, { color: theme.colors.ink }]}>{relationLabel(relation.name)}</Text>
                          <Text style={[styles.relationDetail, { color: theme.colors.muted }]}>{relation.from} → {relation.to}</Text>
                        </View>
                      </View>
                    )) : <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>No declared relations yet.</Text>}
                  </Card>
                </View>

                <View style={styles.column}>
                  <SectionTitle title="Source trust" action="Sources" href="/sources" />
                  <Card style={styles.sourceCard}>
                    {Object.entries(sourceCounts).length ? Object.entries(sourceCounts).map(([source, count], index) => (
                      <View key={source} style={[styles.sourceRow, { borderBottomColor: theme.colors.line }]}>
                        <Pill tone={toneForIndex(index)}>{count}</Pill>
                        <View style={styles.relationCopy}>
                          <Text style={[styles.relationTitle, { color: theme.colors.ink }]}>{source}</Text>
                          <Text style={[styles.relationDetail, { color: theme.colors.muted }]}>Citable records in this collection</Text>
                        </View>
                      </View>
                    )) : <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>No source-backed rows yet.</Text>}
                  </Card>
                </View>
              </View>
            </>
          ) : null}
        </View>
      </ScrollView>
    </Page>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone: 'moss' | 'amber' | 'blue' | 'plum' }) {
  const theme = useLifeOSTheme();
  const backgroundColor = tone === 'moss' ? theme.colors.mossSoft : tone === 'amber' ? theme.colors.amberSoft : tone === 'blue' ? theme.colors.blueSoft : theme.colors.plumSoft;
  return (
    <View style={[styles.stat, { backgroundColor, borderColor: theme.colors.line }]}>
      <Text style={[styles.statValue, { color: theme.colors.ink }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: theme.colors.muted }]}>{label}</Text>
    </View>
  );
}

function RecordRow({ record }: { record: DomainRecordViewModel }) {
  const theme = useLifeOSTheme();
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.recordRow, { borderBottomColor: theme.colors.line }, pressed && styles.pressed]}>
        <View style={[styles.recordIcon, { backgroundColor: theme.colors.canvas }]}><Text style={[styles.recordIconText, { color: theme.colors.moss }]}>{record.collection.slice(0, 1).toUpperCase()}</Text></View>
        <View style={styles.recordCopy}>
          <View style={styles.recordTop}>
            <Text style={[styles.recordTitle, { color: theme.colors.ink }]}>{record.title}</Text>
            <Pill tone={record.tone}>{record.status}</Pill>
          </View>
          <Text style={[styles.recordMeta, { color: theme.colors.muted }]}>{record.meta}</Text>
          <Text style={[styles.recordBody, { color: theme.colors.ink }]} numberOfLines={2}>{record.body}</Text>
          <Text style={[styles.recordSource, { color: theme.colors.moss }]}>{record.source}</Text>
        </View>
        <Text style={[styles.chevron, { color: theme.colors.muted }]}>›</Text>
      </Pressable>
    </Link>
  );
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  hero: { padding: 28, borderRadius: radius.lg },
  heroTop: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 20 },
  heroCopy: { flex: 1, minWidth: 260 },
  kicker: { color: colors.moss, fontSize: 11, fontWeight: '900', letterSpacing: 1.2 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 38, fontWeight: '900', letterSpacing: -1.1, marginTop: 10 },
  subtitle: { color: colors.muted, fontSize: 14, lineHeight: 21, marginTop: 8, maxWidth: 680 },
  heroActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, alignItems: 'flex-start' },
  statGrid: { flexDirection: 'row', gap: 10, marginTop: 22 },
  stack: { flexDirection: 'column' },
  stat: { flexGrow: 1, flexBasis: 0, minHeight: 82, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: 13, justifyContent: 'center' },
  statValue: { color: colors.ink, fontSize: 24, fontWeight: '900' },
  statLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase', marginTop: 4 },
  recordsCard: { paddingVertical: 0 },
  recordRow: { minHeight: 108, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  recordIcon: { width: 42, height: 42, borderRadius: 14, backgroundColor: colors.canvas, alignItems: 'center', justifyContent: 'center' },
  recordIconText: { color: colors.moss, fontSize: 14, fontWeight: '900' },
  recordCopy: { flex: 1, minWidth: 0 },
  recordTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 },
  recordTitle: { color: colors.ink, fontSize: 16, fontWeight: '900', flex: 1 },
  recordMeta: { color: colors.muted, fontSize: 12, marginTop: 4 },
  recordBody: { color: colors.ink, fontSize: 13, lineHeight: 18, marginTop: 7 },
  recordSource: { color: colors.moss, fontSize: 11, fontWeight: '800', marginTop: 7 },
  chevron: { color: colors.muted, fontSize: 28, fontWeight: '300' },
  twoColumn: { flexDirection: 'row', gap: 12, alignItems: 'flex-start' },
  column: { flex: 1, minWidth: 0 },
  relationCard: { paddingVertical: 0 },
  sourceCard: { paddingVertical: 0 },
  relationRow: { minHeight: 70, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  sourceRow: { minHeight: 70, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  relationCopy: { flex: 1, minWidth: 0 },
  relationTitle: { color: colors.ink, fontSize: 14, fontWeight: '900', textTransform: 'capitalize' },
  relationDetail: { color: colors.muted, fontSize: 12, marginTop: 3 },
  emptyCard: { marginTop: 12 },
  emptyState: { paddingVertical: 24, alignItems: 'flex-start' },
  emptyTitle: { color: colors.ink, fontSize: 16, fontWeight: '900' },
  emptyBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 6 },
  emptyActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 16 },
  link: { color: colors.moss, fontSize: 13, fontWeight: '900', marginTop: 14 },
  pressed: { opacity: 0.72 },
});
