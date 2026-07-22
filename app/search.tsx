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

  const items = query ? results : commands;
  return <Page><View style={sharedStyles.content}>
    <View style={styles.searchBox}><Text style={styles.glass}>⌕</Text><TextInput autoFocus value={query} onChangeText={setQuery} placeholder="Search everything or run a command…" placeholderTextColor={colors.muted} style={styles.input} /></View>
    <ScrollView keyboardShouldPersistTaps="handled"><Text style={styles.label}>{query ? `${results.length} MATCHES` : 'QUICK ACTIONS'}</Text><Card style={styles.list}>
      {loading ? <View style={styles.loading}><Text style={styles.loadingText}>Searching records…</Text></View> : null}
      {items.map((item) => {
        const isRecord = 'collection' in item;
        const href = isRecord ? { pathname: '/record/[id]', params: { id: item.id } } : (item as { href: string }).href;
        return <Link href={href} asChild key={item.id}><Pressable style={styles.result}><View style={styles.resultIcon}><Text>{query ? '◉' : item.icon}</Text></View><View style={{ flex: 1 }}><Text style={styles.resultTitle}>{item.title}</Text><Text style={styles.resultDetail}>{query ? item.meta : item.detail}</Text></View>{query ? <Pill tone={(item as DomainRecordViewModel).tone}>{item.status}</Pill> : <Text style={styles.chevron}>›</Text>}</Pressable></Link>;
      })}
      {query && !loading && results.length === 0 ? <View style={styles.empty}><Text style={styles.emptyTitle}>Nothing exact yet</Text><Text style={styles.resultDetail}>Ask LifeOS to search connected sources or the web.</Text><Link href="/(tabs)/chat" style={styles.ask}>Ask with AI →</Link></View> : null}
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
