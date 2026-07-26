import { Link } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Page } from '@/src/components/ui';
import {
  loadCatalog,
  setActiveDomainOverride,
  type MobileSurfaceAction,
  type MobileSurfaceCard,
  type MobileSurfaceQuery,
  type MobileSurfaceTone,
  type MobileSurfaceView,
} from '@/src/domain/catalog';
import { queryDomainCollections } from '@/src/domain/queries';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { radius, type LifeOSColors, useLifeOSTheme } from '@/src/theme';

function matchingRecords(query: MobileSurfaceQuery, records: DomainRecordViewModel[]) {
  const collections = new Set(query.collections);
  const matcher = query.match ? new RegExp(query.match, 'i') : null;
  return records
    .filter((record) => collections.has(record.collection))
    .filter((record) => !matcher || matcher.test([record.title, record.status, record.meta, record.body].join(' ')))
    .slice(0, query.limit);
}

function toneSurface(tone: MobileSurfaceTone, colors: LifeOSColors) {
  if (tone === 'moss') return colors.mossSoft;
  if (tone === 'amber') return colors.amberSoft;
  if (tone === 'plum') return colors.plumSoft;
  if (tone === 'blue') return colors.blueSoft;
  return colors.paper;
}

function toneInk(tone: MobileSurfaceTone, colors: LifeOSColors) {
  if (tone === 'moss') return colors.moss;
  if (tone === 'amber') return colors.amber;
  if (tone === 'plum') return colors.plum;
  if (tone === 'blue') return colors.blue;
  return colors.ink;
}

function SurfaceAction({ action, filled = false }: { action?: MobileSurfaceAction; filled?: boolean }) {
  const theme = useLifeOSTheme();
  if (!action) return null;
  return (
    <Link href={action.href as never} asChild>
      <Pressable
        accessibilityRole="button"
        style={StyleSheet.flatten([
          styles.action,
          {
            backgroundColor: filled ? theme.colors.ink : 'transparent',
            borderColor: filled ? theme.colors.ink : theme.colors.line,
          },
        ])}>
        <Text maxFontSizeMultiplier={1.25} style={[styles.actionText, { color: filled ? theme.colors.paper : theme.colors.ink }]}>{action.label}</Text>
      </Pressable>
    </Link>
  );
}

function SectionHeading({ card }: { card: MobileSurfaceCard }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.sectionHeading}>
      <View style={[styles.sectionMark, { backgroundColor: toneSurface(card.tone, theme.colors) }]}>
        <Text allowFontScaling={false} style={[styles.sectionMarkText, { color: toneInk(card.tone, theme.colors) }]}>{card.icon}</Text>
      </View>
      <Text maxFontSizeMultiplier={1.25} style={[styles.sectionLabel, { color: theme.colors.ink }]}>{card.label}</Text>
    </View>
  );
}

function EmptyContent({ card }: { card: MobileSurfaceCard }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.emptyContent}>
      <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>{card.empty.title}</Text>
      <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>{card.empty.detail}</Text>
    </View>
  );
}

function RecordRow({ card, record }: { card: MobileSurfaceCard; record: DomainRecordViewModel }) {
  const theme = useLifeOSTheme();
  return (
    <Link href={`/record/${encodeURIComponent(record.id)}` as never} asChild>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Open ${record.title}`}
        style={StyleSheet.flatten([styles.recordRow, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }])}>
        <View style={[styles.recordMark, { backgroundColor: toneSurface(card.tone, theme.colors) }]} />
        <View style={styles.recordCopy}>
          <Text maxFontSizeMultiplier={1.25} style={[styles.recordTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
          <Text maxFontSizeMultiplier={1.25} style={[styles.recordDetail, { color: theme.colors.muted }]} numberOfLines={2}>{record.meta || record.body}</Text>
        </View>
        {record.status ? <StatusPill tone={card.tone} status={record.status} /> : null}
      </Pressable>
    </Link>
  );
}

function ListSection({ card, records }: { card: MobileSurfaceCard; records: DomainRecordViewModel[] }) {
  const matches = matchingRecords(card.query, records);
  return (
    <View style={styles.section}>
      <SectionHeading card={card} />
      <View style={styles.recordList}>
        {matches.length ? matches.map((record) => <RecordRow key={record.id} card={card} record={record} />) : <EmptyContent card={card} />}
      </View>
      <SurfaceAction action={card.action} />
    </View>
  );
}

function FaceLine({ label, record, empty }: { label: string; record?: DomainRecordViewModel; empty: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.faceLine, { borderTopColor: theme.colors.line }]}>
      <Text maxFontSizeMultiplier={1.25} style={[styles.faceLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text maxFontSizeMultiplier={1.25} style={[styles.faceValue, { color: theme.colors.ink }]} numberOfLines={1}>{record?.title ?? empty}</Text>
    </View>
  );
}

function StatusPill({ tone, status }: { tone: MobileSurfaceTone; status: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.statusPill, { backgroundColor: toneSurface(tone, theme.colors) }]}>
      <Text maxFontSizeMultiplier={1.15} style={[styles.statusText, { color: theme.colors.ink }]} numberOfLines={1}>{status}</Text>
    </View>
  );
}

function FocusSection({ card, records }: { card: MobileSurfaceCard; records: DomainRecordViewModel[] }) {
  const theme = useLifeOSTheme();
  const primary = matchingRecords(card.query, records)[0];
  return (
    <View style={[
      styles.focusSection,
      {
        backgroundColor: toneSurface(card.tone, theme.colors),
        borderColor: card.tone === 'neutral' ? theme.colors.line : toneSurface(card.tone, theme.colors),
      },
    ]}>
      <SectionHeading card={card} />
      {primary ? (
        <Link href={`/record/${encodeURIComponent(primary.id)}` as never} asChild>
          <Pressable accessibilityRole="button" accessibilityLabel={`Open ${primary.title}`} style={styles.focusBody}>
            <View style={styles.focusTitleRow}>
              <Text maxFontSizeMultiplier={1.25} style={[styles.focusTitle, { color: theme.colors.ink }]} numberOfLines={2}>{primary.title}</Text>
              {primary.status ? <StatusPill tone={card.tone} status={primary.status} /> : null}
            </View>
            <Text maxFontSizeMultiplier={1.25} style={[styles.focusDetail, { color: theme.colors.muted }]} numberOfLines={3}>{primary.meta || primary.body}</Text>
          </Pressable>
        </Link>
      ) : <EmptyContent card={card} />}
      {card.nested.map((nested) => (
        <FaceLine
          key={nested.id}
          label={nested.label}
          record={matchingRecords(nested.query, records)[0]}
          empty={nested.empty}
        />
      ))}
      <SurfaceAction action={card.action} filled={card.kind === 'review'} />
    </View>
  );
}

function LoadingSurface() {
  const theme = useLifeOSTheme();
  return (
    <View accessibilityLabel="Loading Food workspace" style={styles.loading}>
      {[0, 1, 2].map((index) => (
        <View key={index} style={[styles.loadingRow, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
          <View style={[styles.loadingMark, { backgroundColor: theme.colors.line }]} />
          <View style={styles.loadingCopy}>
            <View style={[styles.loadingLine, { backgroundColor: theme.colors.line, width: index === 1 ? '52%' : '68%' }]} />
            <View style={[styles.loadingLine, styles.loadingLineShort, { backgroundColor: theme.colors.line }]} />
          </View>
        </View>
      ))}
    </View>
  );
}

function fallbackView(title: string, subtitle: string, cardOrder: string[]): MobileSurfaceView {
  return { id: 'home', label: 'Home', icon: '⌂', title, subtitle, card_order: cardOrder };
}

/** Generic, package-owned mobile workspace. Domain copy, views, queries and actions live in the active package. */
export function ManifestMobileSurface() {
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeManifest } = loadCatalog();
  const db = useLifeOSDatabase();
  const theme = useLifeOSTheme();
  const surface = activeManifest.mobile_surface;
  const views = useMemo(
    () => surface?.views?.length ? surface.views : surface ? [fallbackView(surface.title, surface.subtitle, surface.card_order)] : [],
    [surface],
  );
  const initialView = surface?.default_view ?? views[0]?.id ?? '';
  const [selectedViewId, setSelectedViewId] = useState(initialView);
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!views.some((view) => view.id === selectedViewId)) setSelectedViewId(surface?.default_view ?? views[0]?.id ?? '');
  }, [selectedViewId, surface?.default_view, views]);

  useEffect(() => {
    let current = true;
    setLoadState('loading');
    queryDomainCollections(db, activeManifest.collections)
      .then((next) => {
        if (!current) return;
        setRecords(next);
        setLoadState('ready');
      })
      .catch(() => {
        if (!current) return;
        setRecords([]);
        setLoadState('error');
      });
    return () => { current = false; };
  }, [activeManifest.collections, activeManifest.id, db, reloadToken]);

  if (!surface) {
    return (
      <Page>
        <View style={styles.centered}>
          <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>This workspace needs a mobile surface.</Text>
          <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>Ask Wonder to create one from the active package.</Text>
        </View>
      </Page>
    );
  }

  const activeView = views.find((view) => view.id === selectedViewId) ?? views[0];
  const cardsById = new Map(surface.cards.map((card) => [card.id, card]));
  const visibleCards = (activeView?.card_order ?? surface.card_order)
    .map((id) => cardsById.get(id))
    .filter((card): card is MobileSurfaceCard => Boolean(card));
  const readyCards = visibleCards.filter((card) => card.kind !== 'review' || matchingRecords(card.query, records).length > 0);

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <View style={styles.header}>
            <View style={styles.headerCopy}>
              <Text maxFontSizeMultiplier={1.2} style={[styles.title, { color: theme.colors.ink }]}>{activeView?.title ?? surface.title}</Text>
              <Text maxFontSizeMultiplier={1.25} style={[styles.subtitle, { color: theme.colors.muted }]}>{activeView?.subtitle ?? surface.subtitle}</Text>
            </View>
            <SurfaceAction action={surface.header_action} filled />
          </View>

          {views.length > 1 ? (
            <ScrollView
              horizontal
              accessibilityRole="tablist"
              contentContainerStyle={styles.viewTabs}
              showsHorizontalScrollIndicator={false}>
              {views.map((view) => {
                const selected = view.id === activeView?.id;
                return (
                  <Pressable
                    key={view.id}
                    accessibilityRole="tab"
                    accessibilityState={{ selected }}
                    onPress={() => setSelectedViewId(view.id)}
                    style={({ pressed }) => [
                      styles.viewTab,
                      {
                        backgroundColor: selected ? theme.colors.ink : theme.colors.paper,
                        borderColor: selected ? theme.colors.ink : theme.colors.line,
                      },
                      pressed && styles.pressed,
                    ]}>
                    <Text allowFontScaling={false} style={styles.viewTabIcon}>{view.icon}</Text>
                    <Text maxFontSizeMultiplier={1.2} style={[styles.viewTabLabel, { color: selected ? theme.colors.paper : theme.colors.ink }]}>{view.label}</Text>
                  </Pressable>
                );
              })}
            </ScrollView>
          ) : null}

          {loadState === 'loading' ? <LoadingSurface /> : null}
          {loadState === 'error' ? (
            <View style={[styles.errorState, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
              <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>Food could not load.</Text>
              <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>Your data is untouched. Try the local view again.</Text>
              <Pressable accessibilityRole="button" onPress={() => setReloadToken((value) => value + 1)} style={[styles.action, { borderColor: theme.colors.line }]}>
                <Text maxFontSizeMultiplier={1.25} style={[styles.actionText, { color: theme.colors.ink }]}>Try again</Text>
              </Pressable>
            </View>
          ) : null}
          {loadState === 'ready' ? (
            <View style={styles.sections}>
              {readyCards.map((card) => card.kind === 'list'
                ? <ListSection key={card.id} card={card} records={records} />
                : <FocusSection key={card.id} card={card} records={records} />)}
            </View>
          ) : null}

          {surface.advanced.visible ? (
            <View style={styles.advanced}>
              {surface.advanced.cards.map((card) => card.kind === 'list'
                ? <ListSection key={card.id} card={card} records={records} />
                : <FocusSection key={card.id} card={card} records={records} />)}
            </View>
          ) : null}
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  content: { width: '100%', maxWidth: 720, alignSelf: 'center', paddingHorizontal: 16, paddingTop: 8, paddingBottom: 28 },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, marginBottom: 12 },
  headerCopy: { flex: 1, minWidth: 0 },
  title: { fontSize: 28, lineHeight: 32, fontWeight: '800', letterSpacing: -0.7 },
  subtitle: { maxWidth: 500, fontSize: 12, lineHeight: 16, marginTop: 2 },
  viewTabs: { gap: 7, paddingBottom: 16 },
  viewTab: { minHeight: 42, borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  viewTabIcon: { fontSize: 14 },
  viewTabLabel: { fontSize: 12, fontWeight: '800' },
  sections: { gap: 18 },
  section: { gap: 7 },
  sectionHeading: { minHeight: 26, flexDirection: 'row', alignItems: 'center', gap: 8 },
  sectionMark: { width: 25, height: 25, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  sectionMarkText: { fontSize: 14, fontWeight: '900' },
  sectionLabel: { flex: 1, fontSize: 16, lineHeight: 20, fontWeight: '800', letterSpacing: -0.2 },
  recordList: { gap: 0 },
  recordRow: { minHeight: 60, borderWidth: 0, borderBottomWidth: StyleSheet.hairlineWidth, borderRadius: 0, paddingVertical: 8, paddingHorizontal: 2, flexDirection: 'row', alignItems: 'center', gap: 10 },
  recordMark: { width: 5, height: 34, borderRadius: 3 },
  recordCopy: { flex: 1, minWidth: 0 },
  recordTitle: { fontSize: 14, lineHeight: 18, fontWeight: '800' },
  recordDetail: { fontSize: 12, lineHeight: 16, marginTop: 2 },
  statusPill: { maxWidth: 118, minHeight: 28, borderRadius: radius.pill, paddingHorizontal: 9, alignItems: 'center', justifyContent: 'center' },
  statusText: { fontSize: 11, lineHeight: 14, fontWeight: '800' },
  focusSection: { borderWidth: 1, borderRadius: radius.md, padding: 13, gap: 8 },
  focusBody: { minHeight: 42, justifyContent: 'center' },
  focusTitleRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 },
  focusTitle: { flex: 1, fontSize: 18, lineHeight: 22, fontWeight: '800', letterSpacing: -0.25 },
  focusDetail: { fontSize: 13, lineHeight: 18, marginTop: 4 },
  faceLine: { minHeight: 42, borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 9, flexDirection: 'row', alignItems: 'center', gap: 8 },
  faceLabel: { fontSize: 11, lineHeight: 15, fontWeight: '800' },
  faceValue: { flex: 1, fontSize: 12, lineHeight: 16, fontWeight: '700' },
  emptyContent: { minHeight: 60, justifyContent: 'center' },
  emptyTitle: { fontSize: 15, lineHeight: 20, fontWeight: '800' },
  emptyDetail: { fontSize: 12, lineHeight: 17, marginTop: 3 },
  action: { minHeight: 40, alignSelf: 'flex-start', borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: 13, alignItems: 'center', justifyContent: 'center' },
  actionText: { fontSize: 12, lineHeight: 16, fontWeight: '800' },
  pressed: { opacity: 0.66 },
  loading: { gap: 8 },
  loadingRow: { minHeight: 72, borderWidth: 1, borderRadius: radius.sm, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 12 },
  loadingMark: { width: 18, height: 36, borderRadius: 7, opacity: 0.65 },
  loadingCopy: { flex: 1, gap: 7 },
  loadingLine: { height: 10, borderRadius: radius.pill, opacity: 0.65 },
  loadingLineShort: { width: '84%', height: 8, opacity: 0.4 },
  errorState: { borderWidth: 1, borderRadius: radius.md, padding: 16, gap: 8 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28 },
  advanced: { marginTop: 24, gap: 18 },
});
