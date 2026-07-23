import { Link, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { loadCatalog } from '@/src/domain/catalog';
import { queryDomainRecords } from '@/src/domain/queries';
import { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';

function countSetting(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const HOME_SECTIONS = ['now', 'review', 'lifeSpaces', 'recent', 'sourceTrust', 'control'] as const;
type HomeSection = typeof HOME_SECTIONS[number];

function pickRecord(records: DomainRecordViewModel[], pattern: RegExp, fallbackIndex: number) {
  return records.find((item) => pattern.test(`${item.collection} ${item.status} ${item.meta} ${item.title} ${item.body}`)) ?? records[fallbackIndex];
}

function orderedSections(value: string, defaults: readonly HomeSection[]) {
  const allowed = new Set(defaults);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is HomeSection => allowed.has(section as HomeSection));
  const missing = defaults.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

export default function TodayScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();
  const settings = useLifeOSSettingsSnapshot();
  const theme = useLifeOSTheme();
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

  const homeConfig = settings.runtime.surfaceConfig.home;
  const reviewLimit = countSetting(homeConfig.reviewLimit, 2);
  const recentLimit = countSetting(homeConfig.recentLimit, 4);
  const rhythmRows = records.slice(0, 3);
  const reviewRows = records.slice(1, 1 + reviewLimit);
  const recentRows = records.slice(0, recentLimit);
  const todayLabel = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date());
  const dinnerRecord = pickRecord(records, /\b(meal|dinner|tonight|planned)\b/i, 0);
  const riskRecord = pickRecord(records, /\b(use soon|pantry|expire|inventory|yogurt)\b/i, 1);
  const shoppingRecord = pickRecord(records, /\b(shopping|buy|to buy|missing)\b/i, 2);
  const firstRecord = dinnerRecord ?? rhythmRows[0];
  const secondRecord = riskRecord ?? rhythmRows[1];
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 36, 280) : '100%';
  const homeSections = orderedSections(homeConfig.sectionOrder, HOME_SECTIONS);

  const renderHomeSection = (section: HomeSection) => {
    switch (section) {
      case 'now':
        return homeConfig.showNowCard ? (
          <Card key={section} tone="moss" style={styles.nowCard}>
            <View style={styles.nowHeader}>
              <Pill tone="moss">{catalog.activeManifest.label.toUpperCase()} ACTIVE</Pill>
              <Text style={[styles.nowDate, { color: theme.colors.muted }]}>{todayLabel}</Text>
            </View>
            <Text style={[styles.nowTitle, compact && styles.nowTitleCompact, { color: theme.colors.ink }]}>{firstRecord?.title ?? `Set up today's ${catalog.activeManifest.label.toLowerCase()} loop`}</Text>
            <Text style={[styles.nowBody, { color: theme.colors.ink }]}>
              {firstRecord?.body || firstRecord?.meta || 'Start with a meal, receipt, pantry item or question. Home should show daily work, not internal machinery.'}
            </Text>
            <View style={styles.nowActions}>
              <ActionButton label={firstRecord ? 'Open now card' : 'Capture first item'} onPress={() => router.push(firstRecord ? `/record/${firstRecord.id}` : '/capture')} />
              <ActionButton label="Ask with context" quiet onPress={() => router.push('/chat')} />
            </View>
            <View style={styles.commandDeck}>
              <HomeCommandCard label="Today decision" title={dinnerRecord?.title ?? 'Pick dinner'} detail={dinnerRecord?.meta ?? 'Choose from Food graph'} tone="moss" href={dinnerRecord ? `/record/${dinnerRecord.id}` : '/chat'} />
              <HomeCommandCard label="Risk" title={riskRecord?.title ?? 'No use-soon risk'} detail={riskRecord?.meta ?? 'Pantry looks calm'} tone="amber" href={riskRecord ? `/record/${riskRecord.id}` : '/sources'} />
              <HomeCommandCard label="Shopping gap" title={shoppingRecord?.title ?? 'Nothing to buy'} detail={shoppingRecord?.meta ?? 'No missing items'} tone="blue" href={shoppingRecord ? `/record/${shoppingRecord.id}` : '/capture'} />
              <HomeCommandCard label="Assistant" title="Ask AI" detail="Use sources, tables, receipts" tone="plum" href="/chat" />
            </View>
          </Card>
        ) : null;
      case 'review':
        return homeConfig.showReviewQueue ? (
          <View key={section}>
            <SectionTitle title="Review queue" action="Ask about today" href="/chat" />
            <Card style={styles.reviewCard}>
              {reviewRows.length ? reviewRows.map((row, index) => (
                <Row
                  key={row.id}
                  icon={index === 0 ? '!' : '↻'}
                  title={index === 0 ? `Review ${row.title}` : row.title}
                  detail={row.meta || `${row.collection} · ${row.status}`}
                  href={{ pathname: '/record/[id]', params: { id: row.id } }}
                />
              )) : (
                <View style={styles.emptyReview}>
                  <Text style={[styles.emptyReviewTitle, { color: theme.colors.ink }]}>No review items yet</Text>
                  <Text style={[styles.emptyReviewBody, { color: theme.colors.muted }]}>Receipts, source conflicts and AI proposals will land here before anything writes to your graph.</Text>
                </View>
              )}
            </Card>
          </View>
        ) : null;
      case 'lifeSpaces':
        return homeConfig.showLifeSpaces ? (
          <View key={section}>
            <SectionTitle title="Life spaces" />
            <View style={sharedStyles.grid}>
              <Link href="/(tabs)/food" asChild>
                <Pressable accessibilityRole="button" style={({ pressed }) => [styles.spacePress, pressed && styles.pressed]}>
                  <Card tone="moss" style={styles.spaceCard}>
                    <Text style={[styles.spaceGlyph, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>F</Text>
                    <Text style={[styles.spaceTitle, { color: theme.colors.ink }]}>{catalog.activeManifest.label}</Text>
                    <Text style={[styles.spaceBody, { color: theme.colors.muted }]}>{records.length || loading ? `${loading ? 'Loading' : records.length} records in your active food graph.` : 'Kitchen, meals, recipes and shopping render from config.'}</Text>
                  </Card>
                </Pressable>
              </Link>
              <Link href="/config" asChild>
                <Pressable accessibilityRole="button" style={({ pressed }) => [styles.spacePress, pressed && styles.pressed]}>
                  <Card tone="plum" style={styles.spaceCard}>
                    <Text style={[styles.spaceGlyph, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>+</Text>
                    <Text style={[styles.spaceTitle, { color: theme.colors.ink }]}>Add a domain</Text>
                    <Text style={[styles.spaceBody, { color: theme.colors.muted }]}>Health, Plants or any future package should arrive by config, not app rebuild.</Text>
                  </Card>
                </Pressable>
              </Link>
            </View>
          </View>
        ) : null;
      case 'recent':
        return homeConfig.showRecentGraph ? (
          <View key={section}>
            <SectionTitle title="Recent graph" action="Open Sources" href="/sources" />
            <Card style={styles.timelineCard}>
              {recentRows.length ? recentRows.map((row) => (
                <Row
                  key={row.id}
                  icon="◉"
                  title={row.title}
                  detail={`${row.collection} · ${row.source}`}
                  href={{ pathname: '/record/[id]', params: { id: row.id } }}
                />
              )) : (
                <Row icon="▣" title="Local graph ready" detail="Connect Notion or Sheets, or capture a record to start the timeline." href="/sources" />
              )}
            </Card>
          </View>
        ) : null;
      case 'sourceTrust':
        return homeConfig.showSourceTrust ? (
          <View key={section}>
            <SectionTitle title="Source trust" />
            <View style={sharedStyles.grid}>
              <Card tone="blue" style={styles.trustCard}>
                <Text style={[styles.trustTitle, { color: theme.colors.ink }]}>Notion and Sheets are optional homes</Text>
                <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>The app can run local-first, then pull chosen providers into one source-backed graph.</Text>
                <Link href="/sources" style={[styles.cardLink, { color: theme.colors.ink }]}>Open trust center →</Link>
              </Card>
              <Card tone="amber" style={styles.trustCard}>
                <Text style={[styles.trustTitle, { color: theme.colors.ink }]}>{secondRecord?.title ?? 'Chat needs citations'}</Text>
                <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>{secondRecord?.meta ?? 'Assistant answers should show tables, records and exact source cards.'}</Text>
                <Link href="/chat" style={[styles.cardLink, { color: theme.colors.ink }]}>Open Chat →</Link>
              </Card>
            </View>
          </View>
        ) : null;
      case 'control':
        return homeConfig.showControlCard ? (
          <View key={section}>
            <SectionTitle title="Control lives in Settings" />
            <Card style={styles.controlCard}>
              <View style={styles.controlCopy}>
                <Text style={[styles.controlTitle, { color: theme.colors.ink }]}>Providers, domains, skills, schemas, screens and MCP are editable from the app.</Text>
                <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>Settings holds the machinery. Home stays useful.</Text>
              </View>
              <View style={styles.controlActions}>
                  <Link href="/settings" style={[styles.controlLink, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>Settings</Link>
                  <Link href="/config" style={[styles.controlLinkQuiet, { borderColor: theme.colors.line, color: theme.colors.ink }]}>Config</Link>
              </View>
            </Card>
          </View>
        ) : null;
      default:
        return null;
    }
  };

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={[styles.content, { width: contentWidth }]}>
          <View style={styles.topbar}>
            <View><Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / HOME</Text><Text style={[styles.date, { color: theme.colors.muted }]}>{todayLabel}</Text></View>
            <View style={styles.topActions}>
              <Link href="/search" asChild><Pressable><Text style={[styles.topIcon, { color: theme.colors.ink }]}>⌕</Text></Pressable></Link>
              <Link href="/capture" asChild><Pressable><Text style={[styles.topIcon, { color: theme.colors.ink }]}>＋</Text></Pressable></Link>
              <Link href="/settings" asChild><Pressable><Text style={[styles.avatar, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>SV</Text></Pressable></Link>
            </View>
          </View>

          {homeSections.map(renderHomeSection)}
        </View>
      </ScrollView>
    </Page>
  );
}

function HomeCommandCard({ label, title, detail, tone, href }: {
  label: string;
  title: string;
  detail: string;
  tone: 'moss' | 'amber' | 'blue' | 'plum';
  href: string;
}) {
  const theme = useLifeOSTheme();
  return (
    <Link href={href as never} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.commandCard, commandToneStyle(tone, theme.colors), pressed && styles.pressed]}>
        <Text style={[styles.commandLabel, { color: theme.colors.muted }]}>{label}</Text>
        <Text style={[styles.commandTitle, { color: theme.colors.ink }]} numberOfLines={1}>{title}</Text>
        <Text style={[styles.commandDetail, { color: theme.colors.muted }]} numberOfLines={1}>{detail}</Text>
      </Pressable>
    </Link>
  );
}

function commandToneStyle(tone: 'moss' | 'amber' | 'blue' | 'plum', themed: typeof colors) {
  if (tone === 'amber') return { backgroundColor: themed.amberSoft, borderColor: themed.line };
  if (tone === 'blue') return { backgroundColor: themed.blueSoft, borderColor: themed.line };
  if (tone === 'plum') return { backgroundColor: themed.plumSoft, borderColor: themed.line };
  return { backgroundColor: themed.mossSoft, borderColor: themed.line };
}

const styles = StyleSheet.create({
  content: { alignSelf: 'center', maxWidth: 1080, paddingBottom: 44 },
  topbar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 2 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 17 },
  topIcon: { color: colors.ink, fontSize: 27 },
  avatar: { width: 32, height: 32, borderRadius: 16, overflow: 'hidden', textAlign: 'center', lineHeight: 32, backgroundColor: colors.ink, color: '#FFF', fontWeight: '800', fontSize: 11 },
  nowCard: { minHeight: 226, padding: 22, overflow: 'hidden' },
  nowHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' },
  nowDate: { color: colors.muted, fontSize: 12, fontWeight: '700' },
  nowTitle: { color: colors.ink, fontSize: 29, lineHeight: 34, fontWeight: '900', letterSpacing: -1, marginTop: 22, maxWidth: 660 },
  nowTitleCompact: { fontSize: 25, lineHeight: 30, maxWidth: 300 },
  nowBody: { color: colors.ink, fontSize: 15, lineHeight: 22, marginTop: 8, maxWidth: 680 },
  nowActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, marginTop: 20 },
  commandDeck: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 20 },
  commandCard: { flexGrow: 1, flexBasis: 190, minHeight: 88, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, padding: 12, justifyContent: 'center' },
  commandLabel: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase' },
  commandTitle: { color: colors.ink, fontSize: 15, fontWeight: '900', marginTop: 8 },
  commandDetail: { color: colors.muted, fontSize: 11, lineHeight: 16, marginTop: 3 },
  reviewCard: { paddingVertical: 0 },
  emptyReview: { paddingVertical: 20 },
  emptyReviewTitle: { color: colors.ink, fontSize: 16, fontWeight: '900' },
  emptyReviewBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 5 },
  spacePress: { flexGrow: 1, flexBasis: 260 },
  spaceCard: { minHeight: 168 },
  spaceGlyph: { width: 42, height: 42, borderRadius: 14, overflow: 'hidden', textAlign: 'center', lineHeight: 42, backgroundColor: colors.ink, color: '#FFF', fontSize: 18, fontWeight: '900' },
  spaceTitle: { color: colors.ink, fontSize: 18, fontWeight: '900', marginTop: 18 },
  spaceBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 6 },
  timelineCard: { paddingVertical: 0 },
  trustCard: { flexGrow: 1, flexBasis: 260, minHeight: 148 },
  trustTitle: { color: colors.ink, fontSize: 16, lineHeight: 21, fontWeight: '900', marginBottom: 7 },
  controlCard: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, alignItems: 'center', marginBottom: 16 },
  controlCopy: { flex: 1, minWidth: 240 },
  controlTitle: { color: colors.ink, fontSize: 16, lineHeight: 21, fontWeight: '900', marginBottom: 4 },
  controlActions: { flexDirection: 'row', gap: 8 },
  controlLink: { minHeight: 42, borderRadius: 999, overflow: 'hidden', backgroundColor: colors.ink, color: '#FFF', paddingHorizontal: 16, paddingVertical: 12, fontSize: 13, fontWeight: '900' },
  controlLinkQuiet: { minHeight: 42, borderRadius: 999, overflow: 'hidden', borderWidth: 1, borderColor: colors.line, color: colors.ink, paddingHorizontal: 16, paddingVertical: 12, fontSize: 13, fontWeight: '900' },
  cardLink: { color: colors.ink, fontSize: 12, fontWeight: '800', marginTop: 14 },
  pressed: { opacity: 0.72 },
});
