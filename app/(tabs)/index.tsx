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

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.topbar}>
            <View><Text style={styles.brand}>LIFEOS</Text><Text style={styles.date}>Wednesday · July 22</Text></View>
            <View style={styles.topActions}>
              <Link href="/search" asChild><Pressable><Text style={styles.topIcon}>⌕</Text></Pressable></Link>
              <Link href="/capture" asChild><Pressable><Text style={styles.topIcon}>＋</Text></Pressable></Link>
              <Link href="/system" asChild><Pressable><Text style={styles.avatar}>SV</Text></Pressable></Link>
            </View>
          </View>
          <PageHeader
            eyebrow="Good morning, Srinivas"
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
            <Metric value={loading ? '…' : `${records.filter((row) => row.collection.includes('recipe')).length}`} label="Recipes" footnote="Quickly reusable" />
            <Metric value={loading ? '…' : `${records.filter((row) => row.collection.includes('shopping')).length}`} label="Shopping items" footnote="Pending and tracked" />
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
              <Text style={styles.cardIcon}>◷</Text><Text style={styles.cardTitle}>Use the yogurt</Text>
              <Text style={sharedStyles.muted}>{catalog.activeManifest.label} surfaces are set to highlight near-term actions.</Text>
              <Link href="/sources" style={styles.cardLink}>Open sources →</Link>
            </Card>
            <Card tone="blue" style={styles.attentionCard}>
              <Text style={styles.cardIcon}>✓</Text><Text style={styles.cardTitle}>Shopping is nearly ready</Text>
              <Text style={sharedStyles.muted}>Review grouped shopping rows before your next run.</Text>
              <Link href="/(tabs)/food" style={styles.cardLink}>Open {catalog.activeManifest.label} workspace →</Link>
            </Card>
          </View>

          <SectionTitle title="Connected context" action="Manage sources" href="/sources" />
          <Card style={styles.listCard}>
            <Row icon="N" title="LifeOS 2026" detail="Notion · configured source" />
            <Row icon="▦" title="LifeOS Master" detail="Google Sheets · configured source" />
            <Row icon="▣" title="Local graph" detail="SQLite · adapter implementation next" />
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
