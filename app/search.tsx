import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Card, Page, PageHeader, Pill, VisualMark, sharedStyles } from '@/src/components/ui';
import { VisualToken } from '@/src/domain/catalog';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { searchDomainRecords } from '@/src/domain/queries';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';
import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { mergeVisualIdentity, visualGlyph } from '@/src/domain/visual-identity';

export default function SearchScreen() {
  const theme = useLifeOSTheme();
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const catalog = loadCatalog();
  const domainLabel = catalog.activeManifest.label;
  const visualIdentity = mergeVisualIdentity(catalog.activeManifest, settings.runtime.visualIdentityOverrides);
  const [query, setQuery] = useState('');
  const db = useLifeOSDatabase();
  const [results, setResults] = useState<DomainRecordViewModel[]>([]);
  const [loading, setLoading] = useState(false);
  const searchConfig = settings.runtime.surfaceConfig.search;
  const resultLimit = countSetting(searchConfig.resultLimit, 8);
  const sections = orderedSections(searchConfig.sectionOrder);

  useEffect(() => {
    let cancelled = false;
    if (!query.trim()) {
      setResults([]);
      setLoading(false);
      return;
    }

    const run = async () => {
      setLoading(true);
      const matches = await searchDomainRecords(db, query);
      if (!cancelled) {
        setResults(matches);
        setLoading(false);
      }
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [db, query, settings.runtime.activeDomain]);

  const hasQuery = query.trim().length > 0;
  const visibleResults = results.slice(0, resultLimit);
  const commands = [
    { id: 'ask', title: `Ask ${domainLabel} AI`, detail: `Search ${domainLabel} sources, saved data and the web`, href: '/(tabs)/chat' as const, visual: visualIdentity.actions?.chat ?? visualIdentity.actions?.ask_with_collection, fallback: '✦' },
    { id: 'capture', title: `Add ${domainLabel.toLowerCase()}`, detail: `Note, receipt, photo, link or quick thought`, href: '/capture' as const, visual: visualIdentity.actions?.capture ?? visualIdentity.actions?.add_record, fallback: '＋' },
    { id: 'settings', title: 'Open Settings', detail: 'AI, sources, domains and app behavior', href: '/settings' as const, visual: visualIdentity.actions?.settings, fallback: '⚙' },
  ];

  const renderSection = (section: SearchSection) => {
    switch (section) {
      case 'hero':
        return searchConfig.showHero ? (
          <View key={section}>
            <View style={styles.contextBar}>
              <View><Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / SEARCH</Text><Text style={[styles.context, { color: theme.colors.muted }]}>{domainLabel} meals, pantry, recipes and shopping</Text></View>
              <Pill tone={hasQuery ? 'moss' : 'blue'}>{hasQuery ? 'Searching' : 'Ready'}</Pill>
            </View>
            <PageHeader eyebrow="Find or act" title={`Search ${domainLabel.toLowerCase()}.`} subtitle={`Find saved ${domainLabel.toLowerCase()} first. If nothing exact exists, ask Chat with the same context.`} />
          </View>
        ) : null;
      case 'quickActions':
        return !hasQuery && searchConfig.showQuickActions ? (
          <View key={section}>
            <Text style={[styles.label, { color: theme.colors.muted }]}>Quick actions</Text>
            <Card style={styles.list}>
              {commands.map((command) => (
                <Link href={command.href} asChild key={command.id}>
                  <Pressable style={styles.result}>
                    <VisualMark token={command.visual as VisualToken | undefined} fallback={command.fallback} size={38} backgroundColor={theme.colors.canvas} label={`${command.title} visual`} style={styles.resultIcon} />
                    <View style={{ flex: 1 }}><Text style={[styles.resultTitle, { color: theme.colors.ink }]}>{command.title}</Text><Text style={[styles.resultDetail, { color: theme.colors.muted }]}>{command.detail}</Text></View>
                    <Text style={[styles.chevron, { color: theme.colors.muted }]}>›</Text>
                  </Pressable>
                </Link>
              ))}
            </Card>
          </View>
        ) : null;
      case 'results':
        return hasQuery && searchConfig.showResults ? (
          <View key={section}>
            <Text style={[styles.label, { color: theme.colors.muted }]}>{`${visibleResults.length} MATCHES`}</Text>
            <Card style={styles.list}>
              {loading ? <View style={styles.loading}><Text style={styles.loadingText}>Searching records…</Text></View> : null}
              {!loading && (
                visibleResults.length ? (
                  visibleResults.map((record) => {
                    const tone = record.tone ?? 'neutral';
                    const status = record.status ?? 'Active';
                    return <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild key={record.id}>
                      <Pressable style={styles.result}>
                        <VisualMark token={visualIdentity.collections?.[record.collection]} fallback="◉" size={38} backgroundColor={theme.colors.canvas} label={`${record.collection} visual`} style={styles.resultIcon} />
                        <View style={{ flex: 1 }}><Text style={[styles.resultTitle, { color: theme.colors.ink }]}>{record.title}</Text><Text style={[styles.resultDetail, { color: theme.colors.muted }]}>{record.meta ?? ''}</Text></View>
                        <Pill tone={tone}>{status}</Pill>
                      </Pressable>
                    </Link>;
                  })
                ) : (
                  <View style={styles.empty}><Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>Nothing exact yet</Text><Text style={[styles.resultDetail, { color: theme.colors.muted }]}>{searchConfig.emptyHint.replace(/\bFood\b/g, domainLabel)}</Text><Link href="/(tabs)/chat" style={[styles.ask, { color: theme.colors.moss }]}>{`Ask ${domainLabel} AI →`}</Link></View>
                )
              )}
            </Card>
          </View>
        ) : null;
      default:
        return null;
    }
  };

  return <Page><View style={sharedStyles.content}>
    <ScrollView keyboardShouldPersistTaps="handled">
      {sections.includes('hero') ? renderSection('hero') : null}
      <View style={[styles.searchBox, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}><Text style={[styles.glass, { color: theme.colors.ink }]}>{visualGlyph(visualIdentity.actions?.search, '⌕')}</Text><TextInput autoFocus value={query} onChangeText={setQuery} placeholder={`Search meals, pantry, recipes, shopping…`} placeholderTextColor={theme.colors.muted} style={[styles.input, { color: theme.colors.ink }]} /></View>
      {sections.filter((section) => section !== 'hero').map(renderSection)}
    </ScrollView>
  </View></Page>;
}

function countSetting(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const SEARCH_SECTIONS = ['hero', 'quickActions', 'results'] as const;
type SearchSection = typeof SEARCH_SECTIONS[number];

function orderedSections(value: string) {
  const allowed = new Set<string>(SEARCH_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is SearchSection => allowed.has(section));
  const missing = SEARCH_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  searchBox: { marginTop: 20, height: 54, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, gap: 10 },
  glass: { color: colors.ink, fontSize: 24 }, input: { flex: 1, color: colors.ink, fontSize: 16 }, label: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2, marginTop: 24, marginBottom: 9 }, list: { paddingVertical: 0 },
  result: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line }, resultIcon: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#ECEBE3', alignItems: 'center', justifyContent: 'center' }, resultTitle: { color: colors.ink, fontSize: 15, fontWeight: '800' }, resultDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 3 }, chevron: { color: colors.muted, fontSize: 26 }, empty: { paddingVertical: 28, alignItems: 'center' }, emptyTitle: { color: colors.ink, fontWeight: '800', fontSize: 16, marginBottom: 6 }, ask: { color: colors.moss, fontWeight: '800', fontSize: 13, marginTop: 15 },
  loading: { paddingVertical: 28, alignItems: 'center' },
  loadingText: { color: colors.muted, fontSize: 13 },
});
