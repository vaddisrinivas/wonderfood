import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { searchDomainRecords } from '@/src/domain/queries';
import { colors, radius, useLifeOSTheme } from '@/src/theme';

const commands = [
  { id: 'ask', title: 'Ask LifeOS', detail: 'Search Notion, Sheets, local data and the web', href: '/(tabs)/chat' as const, icon: '✦' },
  { id: 'capture', title: 'Create a record', detail: 'Note, meal, pantry item, task or link', href: '/capture' as const, icon: '＋' },
  { id: 'settings', title: 'Open Settings', detail: 'Providers, domains, skills, MCP and sources', href: '/settings' as const, icon: '⚙' },
];

export default function SearchScreen() {
  const theme = useLifeOSTheme();
  const [query, setQuery] = useState('');
  const db = useLifeOSDatabase();
  const [results, setResults] = useState<DomainRecordViewModel[]>([]);
  const [loading, setLoading] = useState(false);

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
  }, [db, query]);

  const hasQuery = query.trim().length > 0;

  return <Page><View style={sharedStyles.content}>
    <View style={styles.contextBar}>
      <View><Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / SEARCH</Text><Text style={[styles.context, { color: theme.colors.muted }]}>Records, commands and source-backed answers</Text></View>
      <Pill tone={hasQuery ? 'moss' : 'blue'}>{hasQuery ? 'Searching' : 'Ready'}</Pill>
    </View>
    <PageHeader eyebrow="Find or act" title="Search everything." subtitle="Find local records first. If nothing exact exists, jump into Chat with context instead of staring at a blank result page." />
    <View style={[styles.searchBox, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}><Text style={[styles.glass, { color: theme.colors.ink }]}>⌕</Text><TextInput autoFocus value={query} onChangeText={setQuery} placeholder="Search food, sources, settings, commands…" placeholderTextColor={theme.colors.muted} style={[styles.input, { color: theme.colors.ink }]} /></View>
    <ScrollView keyboardShouldPersistTaps="handled"><Text style={[styles.label, { color: theme.colors.muted }]}>{query ? `${results.length} MATCHES` : 'Quick actions'}</Text><Card style={styles.list}>
      {loading ? <View style={styles.loading}><Text style={styles.loadingText}>Searching records…</Text></View> : null}
      {!loading && hasQuery ? (
        results.length ? (
          results.map((record) => {
            const tone = record.tone ?? 'neutral';
            const status = record.status ?? 'Active';
            return <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild key={record.id}>
              <Pressable style={styles.result}>
                <View style={[styles.resultIcon, { backgroundColor: theme.colors.canvas }]}><Text>◉</Text></View>
                <View style={{ flex: 1 }}><Text style={[styles.resultTitle, { color: theme.colors.ink }]}>{record.title}</Text><Text style={[styles.resultDetail, { color: theme.colors.muted }]}>{record.meta ?? ''}</Text></View>
                <Pill tone={tone}>{status}</Pill>
              </Pressable>
            </Link>;
          })
        ) : (
          <View style={styles.empty}><Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>Nothing exact yet</Text><Text style={[styles.resultDetail, { color: theme.colors.muted }]}>Ask LifeOS to search connected sources or the web.</Text><Link href="/(tabs)/chat" style={[styles.ask, { color: theme.colors.moss }]}>Ask with AI →</Link></View>
        )
      ) : null}
      {!hasQuery ? (
        commands.map((command) => (
          <Link href={command.href} asChild key={command.id}>
            <Pressable style={styles.result}>
              <View style={[styles.resultIcon, { backgroundColor: theme.colors.canvas }]}><Text>{command.icon}</Text></View>
              <View style={{ flex: 1 }}><Text style={[styles.resultTitle, { color: theme.colors.ink }]}>{command.title}</Text><Text style={[styles.resultDetail, { color: theme.colors.muted }]}>{command.detail}</Text></View>
              <Text style={[styles.chevron, { color: theme.colors.muted }]}>›</Text>
            </Pressable>
          </Link>
        ))
      ) : null}
    </Card></ScrollView>
  </View></Page>;
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
