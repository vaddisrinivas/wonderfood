import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Card, Page, Pill, sharedStyles } from '@/src/components/ui';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { searchDomainRecords } from '@/src/domain/queries';
import { colors, radius } from '@/src/theme';

const commands = [
  { id: 'ask', title: 'Ask LifeOS', detail: 'Search Notion, Sheets, local data and the web', href: '/(tabs)/chat' as const, icon: '✦' },
  { id: 'capture', title: 'Create a record', detail: 'Note, meal, pantry item, task or link', href: '/capture' as const, icon: '＋' },
  { id: 'system', title: 'Open system', detail: 'Sources, skills, MCP, agents and domains', href: '/system' as const, icon: '⌘' },
];

export default function SearchScreen() {
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
    <View style={styles.searchBox}><Text style={styles.glass}>⌕</Text><TextInput autoFocus value={query} onChangeText={setQuery} placeholder="Search everything or run a command…" placeholderTextColor={colors.muted} style={styles.input} /></View>
    <ScrollView keyboardShouldPersistTaps="handled"><Text style={styles.label}>{query ? `${results.length} MATCHES` : 'QUICK ACTIONS'}</Text><Card style={styles.list}>
      {loading ? <View style={styles.loading}><Text style={styles.loadingText}>Searching records…</Text></View> : null}
      {!loading && hasQuery ? (
        results.length ? (
          results.map((record) => {
            const tone = record.tone ?? 'neutral';
            const status = record.status ?? 'Active';
            return <Link href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild key={record.id}>
              <Pressable style={styles.result}>
                <View style={styles.resultIcon}><Text>◉</Text></View>
                <View style={{ flex: 1 }}><Text style={styles.resultTitle}>{record.title}</Text><Text style={styles.resultDetail}>{record.meta ?? ''}</Text></View>
                <Pill tone={tone}>{status}</Pill>
              </Pressable>
            </Link>;
          })
        ) : (
          <View style={styles.empty}><Text style={styles.emptyTitle}>Nothing exact yet</Text><Text style={styles.resultDetail}>Ask LifeOS to search connected sources or the web.</Text><Link href="/(tabs)/chat" style={styles.ask}>Ask with AI →</Link></View>
        )
      ) : null}
      {!hasQuery ? (
        commands.map((command) => (
          <Link href={command.href} asChild key={command.id}>
            <Pressable style={styles.result}>
              <View style={styles.resultIcon}><Text>{command.icon}</Text></View>
              <View style={{ flex: 1 }}><Text style={styles.resultTitle}>{command.title}</Text><Text style={styles.resultDetail}>{command.detail}</Text></View>
              <Text style={styles.chevron}>›</Text>
            </Pressable>
          </Link>
        ))
      ) : null}
    </Card></ScrollView>
  </View></Page>;
}

const styles = StyleSheet.create({
  searchBox: { marginTop: 20, height: 54, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15, gap: 10 },
  glass: { color: colors.ink, fontSize: 24 }, input: { flex: 1, color: colors.ink, fontSize: 16 }, label: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.2, marginTop: 24, marginBottom: 9 }, list: { paddingVertical: 0 },
  result: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line }, resultIcon: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#ECEBE3', alignItems: 'center', justifyContent: 'center' }, resultTitle: { color: colors.ink, fontSize: 15, fontWeight: '800' }, resultDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 3 }, chevron: { color: colors.muted, fontSize: 26 }, empty: { paddingVertical: 28, alignItems: 'center' }, emptyTitle: { color: colors.ink, fontWeight: '800', fontSize: 16, marginBottom: 6 }, ask: { color: colors.moss, fontWeight: '800', fontSize: 13, marginTop: 15 },
  loading: { paddingVertical: 28, alignItems: 'center' },
  loadingText: { color: colors.muted, fontSize: 13 },
});
