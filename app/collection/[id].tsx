import { Link, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { loadCatalog, setActiveDomainOverride, VisualToken } from '@/src/domain/catalog';
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

function visualGlyph(token: VisualToken | undefined, fallback: string) {
  return token?.emoji || token?.icon || fallback;
}

function visualAccent(token: VisualToken | undefined): 'moss' | 'amber' | 'blue' | 'plum' | 'neutral' {
  return token?.accent ?? 'neutral';
}

function softForAccent(accent: 'moss' | 'amber' | 'blue' | 'plum' | 'neutral', palette = colors) {
  if (accent === 'moss') return palette.mossSoft;
  if (accent === 'amber') return palette.amberSoft;
  if (accent === 'blue') return palette.blueSoft;
  if (accent === 'plum') return palette.plumSoft;
  return palette.canvas;
}

function inkForAccent(accent: 'moss' | 'amber' | 'blue' | 'plum' | 'neutral', palette = colors) {
  if (accent === 'moss') return palette.moss;
  if (accent === 'amber') return palette.amber;
  if (accent === 'blue') return palette.blue;
  if (accent === 'plum') return palette.plum;
  return palette.ink;
}

function pillToneForVisual(token: VisualToken | undefined, fallbackIndex: number): 'moss' | 'amber' | 'blue' | 'plum' {
  const accent = visualAccent(token);
  return accent === 'neutral' ? toneForIndex(fallbackIndex) : accent;
}

type ViewMode = 'list' | 'board' | 'table';
type FilterMode = 'all' | 'review' | 'source';
type SortMode = 'updated' | 'title' | 'status' | 'source';

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
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [filterMode, setFilterMode] = useState<FilterMode>('all');
  const [sortMode, setSortMode] = useState<SortMode>('updated');
  const [query, setQuery] = useState('');

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
  const collectionVisual = activeManifest.visual_identity?.collections?.[collectionId];
  const collectionGlyph = visualGlyph(collectionVisual, collectionId.slice(0, 1).toUpperCase());
  const collectionAccent = visualAccent(collectionVisual);
  const searchable = query.trim().toLowerCase();
  const visibleRecords = [...collectionRecords]
    .filter((record) => {
      const reviewMatch = /review|need|buy|planned|use|open|tonight/i.test(`${record.status} ${record.meta} ${record.body}`);
      const sourceMatch = Boolean(record.source.trim());
      const filterMatch = filterMode === 'all' || (filterMode === 'review' && reviewMatch) || (filterMode === 'source' && sourceMatch);
      const textMatch = !searchable || [record.title, record.status, record.meta, record.body, record.source].join(' ').toLowerCase().includes(searchable);
      return filterMatch && textMatch;
    })
    .sort((left, right) => {
      if (sortMode === 'title') return left.title.localeCompare(right.title);
      if (sortMode === 'status') return left.status.localeCompare(right.status);
      if (sortMode === 'source') return left.source.localeCompare(right.source);
      return right.id.localeCompare(left.id);
    });
  const propertyFields = [
    ...(activeManifest.provider_template_fields?.required ?? ['id', 'title', 'collection', 'status', 'meta', 'body']),
    ...(activeManifest.provider_template_fields?.rich_detail_json ?? []),
    ...(activeManifest.provider_template_fields?.relations_json ?? []),
  ].filter((field, index, fields) => fields.indexOf(field) === index);

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
                <View style={styles.titleLine}>
                  <View style={[styles.heroGlyph, { backgroundColor: softForAccent(collectionAccent, theme.colors) }]}><Text style={[styles.heroGlyphText, { color: inkForAccent(collectionAccent, theme.colors) }]}>{collectionGlyph}</Text></View>
                  <View style={styles.titleCopy}>
                    <Text style={[styles.kicker, { color: theme.colors.moss }]}>COLLECTION</Text>
                    <Text style={[styles.title, { color: theme.colors.ink }]}>{collectionTitle}</Text>
                  </View>
                </View>
                <Text style={[styles.subtitle, { color: theme.colors.muted }]}>
                  {exists
                    ? `${collectionRecords.length} loaded records. ${incoming.length + outgoing.length} schema relations. ${surfaces.length ? `Visible in ${surfaces.map((surface) => surface.label).join(', ')}.` : 'Support collection for the active graph.'}`
                    : 'This collection is not in the active domain manifest.'}
                </Text>
              </View>
              <View style={styles.heroActions}>
                <ActionButton label={`${visualGlyph(activeManifest.visual_identity?.actions?.add_record, '＋')} Add record`} onPress={() => router.push({ pathname: '/capture', params: { collection: collectionId } })} />
                <ActionButton label={`${visualGlyph(activeManifest.visual_identity?.actions?.ask_with_collection, '✦')} Ask with collection`} quiet onPress={() => router.push('/chat')} />
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
              <Card style={styles.controlDeck}>
                <Text style={[styles.controlTitle, { color: theme.colors.ink }]}>View / Filter / Sort</Text>
                <View style={styles.searchBox}>
                  <Text style={[styles.searchIcon, { color: theme.colors.muted }]}>⌕</Text>
                  <TextInput
                    accessibilityLabel="Filter collection records"
                    value={query}
                    onChangeText={setQuery}
                    placeholder={`Filter ${collectionTitle.toLowerCase()} records`}
                    placeholderTextColor={theme.colors.muted}
                    style={[styles.searchInput, { color: theme.colors.ink }]}
                  />
                </View>
                <View style={styles.controlRows}>
                  <SegmentedControl
                    label="View"
                    options={[
                      ['list', 'List'],
                      ['board', 'Board'],
                      ['table', 'Table'],
                    ]}
                    value={viewMode}
                    onChange={(value) => setViewMode(value as ViewMode)}
                  />
                  <SegmentedControl
                    label="Filter"
                    options={[
                      ['all', 'All'],
                      ['review', 'Review'],
                      ['source', 'Source-backed'],
                    ]}
                    value={filterMode}
                    onChange={(value) => setFilterMode(value as FilterMode)}
                  />
                  <SegmentedControl
                    label="Sort"
                    options={[
                      ['updated', 'Recent'],
                      ['title', 'Title'],
                      ['status', 'Status'],
                      ['source', 'Source'],
                    ]}
                    value={sortMode}
                    onChange={(value) => setSortMode(value as SortMode)}
                  />
                </View>
              </Card>
              <Card style={styles.recordsCard}>
                {loading ? <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>Loading records…</Text> : null}
                {!loading && visibleRecords.length && viewMode === 'list' ? visibleRecords.map((record) => (
                  <RecordRow key={record.id} record={record} visual={activeManifest.visual_identity?.collections?.[record.collection]} />
                )) : null}
                {!loading && visibleRecords.length && viewMode === 'board' ? (
                  <View style={styles.board}>
                    {visibleRecords.map((record) => <BoardCard key={record.id} record={record} visual={activeManifest.visual_identity?.collections?.[record.collection]} />)}
                  </View>
                ) : null}
                {!loading && visibleRecords.length && viewMode === 'table' ? <RecordTable records={visibleRecords} /> : null}
                {!loading && !visibleRecords.length ? (
                  <View style={styles.emptyState}>
                    <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>No {collectionTitle.toLowerCase()} records yet</Text>
                    <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>{collectionRecords.length ? 'No records match this filter.' : 'Capture one, pull Notion/Sheets, or let Chat draft a reviewable record.'}</Text>
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
                  <SectionTitle title="Property kit" action="Edit schema" href="/config" />
                  <Card style={styles.propertyCard}>
                    <Text style={[styles.propertyIntro, { color: theme.colors.muted }]}>Fields, icon, image slot and source identity that Notion, Sheets, SQLite and Chat should preserve for this collection.</Text>
                    <View style={[styles.visualIdentityRow, { backgroundColor: softForAccent(collectionAccent, theme.colors), borderColor: theme.colors.line }]}>
                      <Text style={[styles.visualIdentityGlyph, { color: inkForAccent(collectionAccent, theme.colors) }]}>{collectionGlyph}</Text>
                      <View style={styles.visualIdentityCopy}>
                        <Text style={[styles.visualIdentityTitle, { color: theme.colors.ink }]}>Visual identity</Text>
                        <Text style={[styles.visualIdentityBody, { color: theme.colors.muted }]}>{collectionVisual?.image_url ? `Image: ${collectionVisual.image_url}` : 'No image URL set. Emoji/icon fallback is active.'}</Text>
                      </View>
                    </View>
                    <View style={styles.propertyChips}>
                      {propertyFields.map((field) => (
                        <View key={field} style={[styles.propertyChip, { backgroundColor: theme.colors.canvas, borderColor: theme.colors.line }]}>
                          <Text style={[styles.propertyChipText, { color: theme.colors.ink }]}>{field}</Text>
                        </View>
                      ))}
                    </View>
                    {activeManifest.rich_detail_schema ? <Text style={[styles.schemaLink, { color: theme.colors.moss }]}>{activeManifest.rich_detail_schema}</Text> : null}
                  </Card>
                  <SectionTitle title="Source trust" action="Sources" href="/sources" />
                  <Card style={styles.sourceCard}>
                    {Object.entries(sourceCounts).length ? Object.entries(sourceCounts).map(([source, count], index) => (
                      <View key={source} style={[styles.sourceRow, { borderBottomColor: theme.colors.line }]}>
                        <Pill tone={pillToneForVisual(activeManifest.visual_identity?.sources?.[source], index)}>{visualGlyph(activeManifest.visual_identity?.sources?.[source], String(count))} {count}</Pill>
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

function SegmentedControl({ label, options, value, onChange }: {
  label: string;
  options: Array<[string, string]>;
  value: string;
  onChange: (value: string) => void;
}) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.segmentGroup}>
      <Text style={[styles.segmentLabel, { color: theme.colors.muted }]}>{label}</Text>
      <View style={[styles.segmentTrack, { backgroundColor: theme.colors.canvas }]}>
        {options.map(([id, title]) => (
          <Pressable
            key={id}
            accessibilityRole="button"
            accessibilityState={{ selected: value === id }}
            onPress={() => onChange(id)}
            style={[styles.segmentButton, value === id && { backgroundColor: theme.colors.paper }]}
          >
            <Text style={[styles.segmentText, { color: value === id ? theme.colors.ink : theme.colors.muted }]}>{title}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

function RecordRow({ record, visual }: { record: DomainRecordViewModel; visual?: VisualToken }) {
  const theme = useLifeOSTheme();
  const accent = visualAccent(visual);
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.recordRow, { borderBottomColor: theme.colors.line }, pressed && styles.pressed]}>
        <View style={[styles.recordIcon, { backgroundColor: softForAccent(accent, theme.colors) }]}><Text style={[styles.recordIconText, { color: inkForAccent(accent, theme.colors) }]}>{visualGlyph(visual, record.collection.slice(0, 1).toUpperCase())}</Text></View>
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

function BoardCard({ record, visual }: { record: DomainRecordViewModel; visual?: VisualToken }) {
  const theme = useLifeOSTheme();
  const accent = visualAccent(visual);
  return (
    <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.boardCard, { backgroundColor: theme.colors.canvas, borderColor: theme.colors.line }, pressed && styles.pressed]}>
        <View style={[styles.boardGlyph, { backgroundColor: softForAccent(accent, theme.colors) }]}><Text style={[styles.boardGlyphText, { color: inkForAccent(accent, theme.colors) }]}>{visualGlyph(visual, record.collection.slice(0, 1).toUpperCase())}</Text></View>
        <View style={styles.recordTop}>
          <Text style={[styles.recordTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
          <Pill tone={record.tone}>{record.status}</Pill>
        </View>
        <Text style={[styles.recordMeta, { color: theme.colors.muted }]}>{record.meta}</Text>
        <Text style={[styles.recordBody, { color: theme.colors.ink }]} numberOfLines={3}>{record.body}</Text>
        <Text style={[styles.recordSource, { color: theme.colors.moss }]}>{record.source}</Text>
      </Pressable>
    </Link>
  );
}

function RecordTable({ records }: { records: DomainRecordViewModel[] }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.table, { borderColor: theme.colors.line }]}>
      <View style={[styles.tableRow, styles.tableHeader, { borderBottomColor: theme.colors.line }]}>
        <Text style={[styles.tableHead, { color: theme.colors.muted }]}>Title</Text>
        <Text style={[styles.tableHead, { color: theme.colors.muted }]}>Status</Text>
        <Text style={[styles.tableHead, { color: theme.colors.muted }]}>Source</Text>
      </View>
      {records.map((record) => (
        <Link key={record.id} href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
          <Pressable accessibilityRole="button" style={({ pressed }) => [styles.tableRow, { borderBottomColor: theme.colors.line }, pressed && styles.pressed]}>
            <Text style={[styles.tableCell, styles.tableTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
            <Text style={[styles.tableCell, { color: theme.colors.ink }]} numberOfLines={1}>{record.status}</Text>
            <Text style={[styles.tableCell, { color: theme.colors.muted }]} numberOfLines={1}>{record.source}</Text>
          </Pressable>
        </Link>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  hero: { padding: 28, borderRadius: radius.lg },
  heroTop: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 20 },
  heroCopy: { flex: 1, minWidth: 260 },
  titleLine: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  heroGlyph: { width: 58, height: 58, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  heroGlyphText: { fontSize: 27, fontWeight: '900' },
  titleCopy: { flex: 1, minWidth: 0 },
  kicker: { color: colors.moss, fontSize: 11, fontWeight: '900', letterSpacing: 1.2 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 38, fontWeight: '900', letterSpacing: -1.1, marginTop: 6 },
  subtitle: { color: colors.muted, fontSize: 14, lineHeight: 21, marginTop: 8, maxWidth: 680 },
  heroActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, alignItems: 'flex-start' },
  statGrid: { flexDirection: 'row', gap: 10, marginTop: 22 },
  stack: { flexDirection: 'column' },
  stat: { flexGrow: 1, flexBasis: 0, minHeight: 82, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: 13, justifyContent: 'center' },
  statValue: { color: colors.ink, fontSize: 24, fontWeight: '900' },
  statLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase', marginTop: 4 },
  controlDeck: { gap: 14 },
  controlTitle: { color: colors.ink, fontSize: 13, fontWeight: '900' },
  searchBox: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14 },
  searchIcon: { color: colors.muted, fontSize: 18 },
  searchInput: { flex: 1, color: colors.ink, fontSize: 14, minHeight: 44 },
  controlRows: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  segmentGroup: { flexGrow: 1, flexBasis: 220 },
  segmentLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 6 },
  segmentTrack: { flexDirection: 'row', flexWrap: 'wrap', gap: 4, borderRadius: radius.pill, backgroundColor: colors.canvas, padding: 4 },
  segmentButton: { minHeight: 36, borderRadius: radius.pill, paddingHorizontal: 11, alignItems: 'center', justifyContent: 'center' },
  segmentText: { color: colors.muted, fontSize: 12, fontWeight: '900' },
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
  board: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, paddingVertical: 16 },
  boardCard: { flexGrow: 1, flexBasis: 240, minHeight: 152, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: 14 },
  boardGlyph: { width: 38, height: 38, borderRadius: 14, alignItems: 'center', justifyContent: 'center', marginBottom: 10 },
  boardGlyphText: { fontSize: 18, fontWeight: '900' },
  table: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, overflow: 'hidden', marginVertical: 16 },
  tableRow: { minHeight: 48, flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  tableHeader: { minHeight: 40, backgroundColor: '#F4F5EF' },
  tableHead: { flex: 1, color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase', paddingHorizontal: 12 },
  tableCell: { flex: 1, color: colors.ink, fontSize: 12, paddingHorizontal: 12 },
  tableTitle: { fontWeight: '900' },
  twoColumn: { flexDirection: 'row', gap: 12, alignItems: 'flex-start' },
  column: { flex: 1, minWidth: 0 },
  relationCard: { paddingVertical: 0 },
  sourceCard: { paddingVertical: 0 },
  propertyCard: { marginBottom: 8 },
  propertyIntro: { color: colors.muted, fontSize: 13, lineHeight: 19 },
  visualIdentityRow: { marginTop: 12, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 12 },
  visualIdentityGlyph: { width: 36, fontSize: 24, fontWeight: '900', textAlign: 'center' },
  visualIdentityCopy: { flex: 1, minWidth: 0 },
  visualIdentityTitle: { color: colors.ink, fontSize: 13, fontWeight: '900' },
  visualIdentityBody: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 2 },
  propertyChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12 },
  propertyChip: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, paddingVertical: 7, paddingHorizontal: 10, backgroundColor: colors.canvas },
  propertyChipText: { color: colors.ink, fontSize: 11, fontWeight: '900' },
  schemaLink: { color: colors.moss, fontSize: 12, fontWeight: '900', marginTop: 12 },
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
