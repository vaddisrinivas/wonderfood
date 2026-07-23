import { Link, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ActionButton, Card, Metric, Page, PageHeader, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { loadCatalog } from '@/src/domain/catalog';
import { queryDomainRecords } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { colors } from '@/src/theme';

export default function TodayScreen() {
  const router = useRouter();
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();
  const [loading, setLoading] = useState(true);
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      const items = await queryDomainRecords(db);
      if (!cancelled) {
        setRecords(items);
        setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [db]);

  const rhythmRows = records.slice(0, 3);
  const todayLabel = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date());

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.topbar}>
            <View><Text style={styles.brand}>LIFEOS</Text><Text style={styles.date}>{todayLabel}</Text></View>
            <View style={styles.topActions}>
              <Link href="/search" asChild><Pressable><Text style={styles.topIcon}>⌕</Text></Pressable></Link>
              <Link href="/capture" asChild><Pressable><Text style={styles.topIcon}>＋</Text></Pressable></Link>
              <Link href="/system" asChild><Pressable><Text style={styles.avatar}>SV</Text></Pressable></Link>
            </View>
          </View>
          <PageHeader
            eyebrow={`Your ${catalog.activeManifest.label} LifeOS`}
            title="A calm day starts here."
            subtitle={`One view across ${catalog.activeManifest.label} and connected sources.`}
          />

          <Card tone="moss" style={styles.hero}>
            <View style={styles.heroCopy}>
              <Pill tone="moss">NEXT · 7:30 PM</Pill>
              <Text style={styles.heroTitle}>{rhythmRows[0]?.title ?? `Start your ${catalog.activeManifest.label} loop`}</Text>
              <Text style={sharedStyles.body}>{rhythmRows[0]?.meta ?? 'Records will appear here as soon as source sync lands.'}</Text>
              <View style={styles.heroActions}>
                <ActionButton label="Open first record" onPress={() => router.push(rhythmRows[0] ? `/record/${rhythmRows[0].id}` : '/capture')} />
                <ActionButton label="Ask AI" quiet onPress={() => router.push('/chat')} />
              </View>
            </View>
            <Text style={styles.heroGlyph}>◒</Text>
          </Card>

          <View style={[sharedStyles.grid, styles.metrics]}>
            <Metric value={loading ? '…' : `${records.length}`} label={`${catalog.activeManifest.label} records`} footnote="Total across this domain" />
            <Metric value={`${catalog.activeManifest.collections.length}`} label="Collections" footnote="Defined by this package" />
            <Metric value={`${catalog.activeManifest.surfaces.length}`} label="Surfaces" footnote="Rendered from config" />
          </View>

          <SectionTitle title="Today’s rhythm" action="Ask about today" href="/chat" />
          <Card style={styles.listCard}>
            {rhythmRows.length ? rhythmRows.map((row) => (
              <Row
                key={row.id}
                icon="◉"
                title={row.title}
                detail={row.meta || `${row.collection} · ${row.status}`}
                href={{ pathname: '/record/[id]', params: { id: row.id } }}
              />
            )) : <Row icon="⌁" title={`No ${catalog.activeManifest.label} rows yet`} detail="Capture something to seed your timeline" href="/capture" />}
          </Card>

          <SectionTitle title="Worth your attention" />
          <View style={sharedStyles.grid}>
            <Card tone="amber" style={styles.attentionCard}>
              <Text style={styles.cardIcon}>◷</Text><Text style={styles.cardTitle}>{rhythmRows[1]?.title ?? 'No urgent item'}</Text>
              <Text style={sharedStyles.muted}>{rhythmRows[1]?.meta ?? `${catalog.activeManifest.label} will surface near-term actions here.`}</Text>
              <Link href="/sources" style={styles.cardLink}>Open sources →</Link>
            </Card>
            <Card tone="blue" style={styles.attentionCard}>
              <Text style={styles.cardIcon}>✓</Text><Text style={styles.cardTitle}>Your package is configurable</Text>
              <Text style={sharedStyles.muted}>Change domains, skills, agents, workflows, schemas and sync without rebuilding.</Text>
              <Link href="/(tabs)/food" style={styles.cardLink}>Open {catalog.activeManifest.label} workspace →</Link>
            </Card>
          </View>

          <SectionTitle title="Connected context" action="Manage sources" href="/sources" />
          <Card style={styles.listCard}>
            <Row icon="▣" title="Local graph" detail="SQLite · available offline" />
            <Row icon="N" title="Notion" detail="Optional · configure in Connections" href="/settings" />
            <Row icon="▦" title="Google Sheets" detail="Optional · configure in Connections" href="/settings" />
          </Card>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  topbar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 2 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 17 },
  topIcon: { color: colors.ink, fontSize: 27 },
  avatar: { width: 32, height: 32, borderRadius: 16, overflow: 'hidden', textAlign: 'center', lineHeight: 32, backgroundColor: colors.ink, color: '#FFF', fontWeight: '800', fontSize: 11 },
  hero: { minHeight: 196, flexDirection: 'row', alignItems: 'center', overflow: 'hidden', padding: 20 },
  heroCopy: { flex: 1, zIndex: 1 },
  heroTitle: { color: colors.ink, fontSize: 25, lineHeight: 30, fontWeight: '800', letterSpacing: -0.7, marginTop: 18, marginBottom: 5 },
  heroGlyph: { fontSize: 116, color: '#A6B69A', opacity: 0.36, marginRight: -28 },
  heroActions: { flexDirection: 'row', gap: 9, marginTop: 18 },
  metrics: { marginTop: 12 },
  listCard: { paddingVertical: 0 },
  attentionCard: { flexGrow: 1, flexBasis: 260, minHeight: 152 },
  cardIcon: { fontSize: 22, marginBottom: 12 },
  cardTitle: { color: colors.ink, fontSize: 16, fontWeight: '800', marginBottom: 7 },
  cardLink: { color: colors.ink, fontSize: 12, fontWeight: '800', marginTop: 14 },
});
