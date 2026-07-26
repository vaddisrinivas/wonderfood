import { Link } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Card, Page, Pill, VisualMark } from '@/src/components/ui';
import { loadCatalog, setActiveDomainOverride, type MobileSurfaceCard, type MobileSurfaceQuery, type MobileSurfaceTone } from '@/src/domain/catalog';
import { queryDomainCollections } from '@/src/domain/queries';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { radius, useLifeOSTheme } from '@/src/theme';

const CARD_TONES: Record<MobileSurfaceTone, 'moss' | 'amber' | 'plum' | 'blue' | undefined> = {
  neutral: undefined,
  moss: 'moss',
  amber: 'amber',
  plum: 'plum',
  blue: 'blue',
};

function matchingRecords({ query }: { query: MobileSurfaceQuery }, records: DomainRecordViewModel[]) {
  const collections = new Set(query.collections);
  const matcher = query.match ? new RegExp(query.match, 'i') : null;
  return records
    .filter((record) => collections.has(record.collection))
    .filter((record) => !matcher || matcher.test([record.title, record.status, record.meta, record.body].join(' ')))
    .slice(0, query.limit);
}

function FaceLine({ label, record, empty }: { label: string; record?: DomainRecordViewModel; empty: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.nestedLine, { borderTopColor: theme.colors.line }]}>
      <Text style={[styles.nestedLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text style={[styles.nestedValue, { color: theme.colors.ink }]} numberOfLines={1}>{record?.title ?? empty}</Text>
    </View>
  );
}

function ManifestCard({ card, records, density }: { card: MobileSurfaceCard; records: DomainRecordViewModel[]; density: 'compact' | 'comfortable' }) {
  const theme = useLifeOSTheme();
  const primary = matchingRecords(card, records)[0];
  const content = (
    <Card tone={CARD_TONES[card.tone]} style={[styles.card, density === 'compact' && styles.cardCompact]}>
      <View style={styles.cardHeader}>
        <View style={styles.cardHeading}>
          <VisualMark fallback={card.icon} size={30} backgroundColor={theme.colors.paper} />
          <Text style={[styles.cardLabel, { color: theme.colors.ink }]}>{card.label}</Text>
        </View>
        {primary?.status ? <Pill tone={card.tone}>{primary.status}</Pill> : null}
      </View>
      <Text style={[styles.cardTitle, { color: theme.colors.ink }]} numberOfLines={2}>{primary?.title ?? card.empty.title}</Text>
      <Text style={[styles.cardDetail, { color: theme.colors.muted }]} numberOfLines={2}>{primary?.meta || primary?.body || card.empty.detail}</Text>
      {card.nested.map((nested) => <FaceLine key={nested.id} label={nested.label} record={matchingRecords(nested, records)[0]} empty={nested.empty} />)}
      {card.action ? (
        <Link href={card.action.href as never} asChild>
          <Pressable accessibilityRole="button" style={({ pressed }) => [styles.action, { borderColor: theme.colors.line }, pressed && styles.pressed]}>
            <Text style={[styles.actionText, { color: theme.colors.ink }]}>{card.action.label} →</Text>
          </Pressable>
        </Link>
      ) : null}
    </Card>
  );

  return <View style={styles.cardWrap}>{content}</View>;
}

/** Renders a domain-owned mobile surface; no domain copy, ordering, or routes live here. */
export function ManifestMobileSurface() {
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeManifest } = loadCatalog();
  const db = useLifeOSDatabase();
  const theme = useLifeOSTheme();
  const { width } = useWindowDimensions();
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);
  const surface = activeManifest.mobile_surface;
  const compact = width < 680;
  const cards = useMemo(() => {
    if (!surface) return [];
    const byId = new Map(surface.cards.map((card) => [card.id, card]));
    return surface.card_order.map((id) => byId.get(id)).filter((card): card is MobileSurfaceCard => Boolean(card));
  }, [surface]);

  useEffect(() => {
    let current = true;
    queryDomainCollections(db, activeManifest.collections)
      .then((next) => { if (current) setRecords(next); })
      .catch(() => { if (current) setRecords([]); });
    return () => { current = false; };
  }, [activeManifest.collections, activeManifest.id, db]);

  if (!surface) {
    return (
      <Page>
        <View style={styles.emptyPage}><Text style={[styles.emptyText, { color: theme.colors.muted }]}>This life space has no mobile surface yet.</Text></View>
      </Page>
    );
  }

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
        <View style={[styles.content, compact && styles.contentCompact]}>
          <View style={styles.header}>
            <View>
              <Text style={[styles.eyebrow, { color: theme.colors.moss }]}>{activeManifest.label.toUpperCase()}</Text>
              <Text style={[styles.title, { color: theme.colors.ink }]}>{surface.title}</Text>
              <Text style={[styles.subtitle, { color: theme.colors.muted }]}>{surface.subtitle}</Text>
            </View>
            {surface.header_action ? (
              <Link href={surface.header_action.href as never} asChild>
                <Pressable accessibilityRole="button" style={({ pressed }) => [styles.headerAction, { backgroundColor: theme.colors.ink }, pressed && styles.pressed]}>
                  <Text style={[styles.headerActionText, { color: theme.colors.paper }]}>{surface.header_action.label}</Text>
                </Pressable>
              </Link>
            ) : null}
          </View>
          <View style={[styles.grid, compact && styles.gridCompact]}>
            {cards.map((card) => <ManifestCard key={card.id} card={card} records={records} density={surface.card_density} />)}
          </View>
          {surface.advanced.visible ? (
            <View style={styles.advanced}>
              {surface.advanced.cards.map((card) => <ManifestCard key={card.id} card={card} records={records} density={surface.card_density} />)}
            </View>
          ) : null}
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  content: { width: '100%', maxWidth: 760, alignSelf: 'center', paddingHorizontal: 18, paddingTop: 12, paddingBottom: 42 },
  contentCompact: { paddingHorizontal: 14, paddingBottom: 28 },
  header: { flexDirection: 'row', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', marginBottom: 14 },
  eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.1 },
  title: { fontSize: 27, lineHeight: 31, fontWeight: '800', letterSpacing: -0.8, marginTop: 3 },
  subtitle: { maxWidth: 480, fontSize: 13, lineHeight: 18, marginTop: 4 },
  headerAction: { minHeight: 34, paddingHorizontal: 13, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center', marginTop: 5 },
  headerActionText: { fontSize: 12, fontWeight: '800' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  gridCompact: { gap: 8 },
  cardWrap: { width: '48.6%', minWidth: 0, flexGrow: 1 },
  card: { minHeight: 168, padding: 13 },
  cardCompact: { minHeight: 144, padding: 11 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  cardHeading: { flexDirection: 'row', alignItems: 'center', gap: 8, minWidth: 0, flex: 1 },
  cardLabel: { fontSize: 13, fontWeight: '800', flexShrink: 1 },
  cardTitle: { fontSize: 16, lineHeight: 20, fontWeight: '800', letterSpacing: -0.25, marginTop: 10 },
  cardDetail: { fontSize: 12, lineHeight: 16, marginTop: 4 },
  nestedLine: { flexDirection: 'row', alignItems: 'center', gap: 7, borderTopWidth: StyleSheet.hairlineWidth, marginTop: 8, paddingTop: 7 },
  nestedLabel: { fontSize: 10, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.45 },
  nestedValue: { fontSize: 11, fontWeight: '700', flex: 1 },
  action: { alignSelf: 'flex-start', borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: 10, paddingVertical: 6, marginTop: 10 },
  actionText: { fontSize: 11, fontWeight: '800' },
  pressed: { opacity: 0.66 },
  advanced: { marginTop: 18, gap: 8 },
  emptyPage: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28 },
  emptyText: { fontSize: 14, textAlign: 'center' },
});
