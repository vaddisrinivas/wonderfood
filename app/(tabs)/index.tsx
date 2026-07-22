import { Link, useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ActionButton, Card, Metric, Page, PageHeader, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { colors } from '@/src/theme';

export default function TodayScreen() {
  const router = useRouter();
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
          <PageHeader eyebrow="Good morning, Srinivas" title="A calm day starts here." subtitle="One view across your meals, kitchen, notes and connected data." />

          <Card tone="moss" style={styles.hero}>
            <View style={styles.heroCopy}>
              <Pill tone="moss">NEXT · 7:30 PM</Pill>
              <Text style={styles.heroTitle}>Sheet-pan tandoori chicken</Text>
              <Text style={sharedStyles.body}>35 min · everything is already in the kitchen</Text>
              <View style={styles.heroActions}>
                <ActionButton label="Open meal" onPress={() => router.push('/record/recipe-tandoori')} />
                <ActionButton label="Ask AI" quiet onPress={() => router.push('/chat')} />
              </View>
            </View>
            <Text style={styles.heroGlyph}>◒</Text>
          </Card>

          <View style={[sharedStyles.grid, styles.metrics]}>
            <Metric value="3" label="Meals planned" footnote="Breakfast → dinner" />
            <Metric value="2" label="Use soon" footnote="No food wasted" />
            <Metric value="8" label="Shopping items" footnote="2 already at home" />
          </View>

          <SectionTitle title="Today’s rhythm" action="Ask about today" href="/chat" />
          <Card style={styles.listCard}>
            <Row icon="☀" title="Greek yogurt bowl" detail="Breakfast · 8:30 AM · 24g protein" href="/record/pantry-yogurt" />
            <Row icon="◐" title="Leftover green dal" detail="Lunch · 1:00 PM · ready to heat" href="/record/meal-green-dal" />
            <Row icon="☾" title="Tandoori chicken" detail="Dinner · 7:30 PM · 35 min" href="/record/recipe-tandoori" />
          </Card>

          <SectionTitle title="Worth your attention" />
          <View style={sharedStyles.grid}>
            <Card tone="amber" style={styles.attentionCard}>
              <Text style={styles.cardIcon}>◷</Text><Text style={styles.cardTitle}>Use the yogurt</Text>
              <Text style={sharedStyles.muted}>Expires Friday. Tonight’s marinade uses half.</Text>
              <Link href="/record/pantry-yogurt" style={styles.cardLink}>See pantry item →</Link>
            </Card>
            <Card tone="blue" style={styles.attentionCard}>
              <Text style={styles.cardIcon}>✓</Text><Text style={styles.cardTitle}>Shopping is nearly ready</Text>
              <Text style={sharedStyles.muted}>Review 8 items before your next store run.</Text>
              <Link href="/(tabs)/food" style={styles.cardLink}>Open food workspace →</Link>
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
