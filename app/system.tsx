import { Link } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Card, Page, Pill, SectionTitle } from '@/src/components/ui';
import { colors, radius } from '@/src/theme';
import catalog from '@/packages/domain-config/domain-catalog.v1.json';

type Tone = 'moss' | 'amber' | 'plum' | 'blue';

const domains = {
  Food: {
    tone: 'moss' as Tone,
    glyph: 'F',
    title: 'Food command space',
    detail: 'Kitchen, meals, recipes, shopping and spend are one editable graph.',
    href: '/(tabs)/food',
  },
  Health: {
    tone: 'plum' as Tone,
    glyph: 'H',
    title: 'Health space',
    detail: 'Health Connect, symptoms, sleep, labs and habits can join when enabled.',
    href: '/config',
  },
  Plants: {
    tone: 'blue' as Tone,
    glyph: 'P',
    title: 'Plants space',
    detail: 'Rooms, species, watering, light and problems use the same runtime.',
    href: '/config',
  },
} as const;

const controlTiles = [
  ['Sources', 'Notion, Sheets, local graph and sync receipts.', 'Open trust center', '/sources', 'blue'],
  ['AI and chat', 'Providers, fallback, citations and source-bounded answers.', 'Tune assistant', '/settings', 'plum'],
  ['Domains', 'Pick Food now; add Health, Plants or any future package from config.', 'Edit packages', '/config', 'moss'],
  ['Skills and MCP', 'Tools, resources and instructions shared by app and external assistants.', 'Inspect contracts', '/config', 'amber'],
  ['Schemas', 'Properties, relations, rollups, provenance and archive rules.', 'Edit schema layer', '/config', 'blue'],
  ['Privacy', 'No mandatory hosted bridge. Tokens stay provider-scoped and app editable.', 'Review settings', '/settings', 'moss'],
] as const;

const homeModel = [
  ['Home', 'Now card, review queue, active domains, recent changes.'],
  ['Domain', 'Config-driven workspace: overview, views, tables, boards and record pages.'],
  ['Chat', 'GPT-like threads with citations, tables, source cards and reviewable actions.'],
  ['Sources', 'Trust center for Notion, Sheets, local, Postgres and import/export.'],
  ['Settings', 'All providers, domains, skills, schemas, MCP and sync editable in-app.'],
] as const;

function toneSoft(tone: Tone) {
  return { moss: colors.mossSoft, amber: colors.amberSoft, plum: colors.plumSoft, blue: colors.blueSoft }[tone];
}

function toneInk(tone: Tone) {
  return { moss: colors.moss, amber: colors.amber, plum: colors.plum, blue: colors.blue }[tone];
}

export default function SystemScreen() {
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 36, 280) : '100%';

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={[styles.content, { width: contentWidth }]}>
          <View style={styles.topbar}>
            <View>
              <Text style={styles.brand}>LIFEOS / CONTROL DECK</Text>
              <Text style={styles.context}>Advanced settings, not your daily home</Text>
            </View>
            <Link href="/settings" asChild>
              <Pressable accessibilityRole="button" style={({ pressed }) => [styles.settingsButton, pressed && styles.pressed]}>
                <Text style={styles.settingsButtonText}>Settings</Text>
              </Pressable>
            </Link>
          </View>

          <Card style={[styles.hero, compact && styles.heroCompact]}>
            <View style={styles.heroCopy}>
              <Pill tone="moss">PORTABLE LIFEOS</Pill>
              <Text style={[styles.heroTitle, compact && styles.heroTitleCompact]}>Your app should feel like Notion plus GPT, not a status page.</Text>
              <Text style={[styles.heroBody, compact && styles.heroBodyCompact]}>
                Daily work belongs on Home and Domain pages. This deck is only for changing the system: sources,
                domains, skills, schemas, providers and sync.
              </Text>
              <View style={styles.heroActions}>
                <Link href="/" style={styles.primaryAction}>Open Home</Link>
                <Link href="/(tabs)/food" style={styles.secondaryAction}>Open Food</Link>
              </View>
            </View>
            <View style={[styles.stackGraphic, compact && styles.stackGraphicCompact]} accessible accessibilityLabel="LifeOS stack: Home, Domain, Chat, Sources and Settings">
              {homeModel.map(([name], index) => (
                <View key={name} style={[styles.stackLayer, { transform: [{ translateY: index * -6 }] }]}>
                  <Text style={styles.stackLayerText}>{name}</Text>
                </View>
              ))}
            </View>
          </Card>

          <SectionTitle title="Screen model" />
          <Card style={styles.modelCard}>
            {homeModel.map(([name, detail], index) => (
              <View key={name} style={[styles.modelRow, index === 0 && styles.modelRowFirst]}>
                <Text style={styles.modelNumber}>{String(index + 1).padStart(2, '0')}</Text>
                <View style={styles.modelCopy}>
                  <Text style={styles.modelTitle}>{name}</Text>
                  <Text style={[styles.modelDetail, compact && styles.modelDetailCompact]}>{detail}</Text>
                </View>
              </View>
            ))}
          </Card>

          <SectionTitle title="Active and available spaces" />
          <View style={styles.domainGrid}>
            {catalog.domains.map(({ label, status }) => {
              const domain = domains[label as keyof typeof domains];
              return (
                <Link key={label} href={domain.href} asChild>
                  <Pressable accessibilityRole="button" style={({ pressed }) => [styles.domainPress, pressed && styles.pressed]}>
                    <Card tone={domain.tone} style={styles.domainCard}>
                      <View style={styles.domainTop}>
                        <View style={[styles.domainGlyph, { backgroundColor: toneInk(domain.tone) }]}>
                          <Text style={styles.domainGlyphText}>{domain.glyph}</Text>
                        </View>
                        <Pill tone={domain.tone}>{status.toUpperCase()}</Pill>
                      </View>
                      <Text style={styles.domainTitle}>{domain.title}</Text>
                      <Text style={[styles.domainDetail, compact && styles.domainDetailCompact]}>{domain.detail}</Text>
                      <Text style={[styles.domainLink, { color: toneInk(domain.tone) }]}>Open or configure →</Text>
                    </Card>
                  </Pressable>
                </Link>
              );
            })}
          </View>

          <SectionTitle title="Configure from the app" />
          <View style={styles.tileGrid}>
            {controlTiles.map(([title, detail, action, href, tone]) => (
              <Link key={title} href={href} asChild>
                <Pressable accessibilityRole="button" style={({ pressed }) => [styles.tilePress, pressed && styles.pressed]}>
                  <View style={[styles.controlTile, { backgroundColor: toneSoft(tone as Tone) }]}>
                    <Text style={[styles.tileKicker, { color: toneInk(tone as Tone) }]}>{title}</Text>
                    <Text style={[styles.tileDetail, compact && styles.tileDetailCompact]}>{detail}</Text>
                    <Text style={styles.tileAction}>{action} →</Text>
                  </View>
                </Pressable>
              </Link>
            ))}
          </View>

          <SectionTitle title="Glance-style config, LifeOS-grade model" />
          <Card style={styles.yamlCard}>
            <View style={styles.yamlText}>
              <Text style={styles.yamlTitle}>Borrow the YAML idea. Do not become YAML-first.</Text>
              <Text style={styles.yamlBody}>
                A portable LifeOS profile can describe pages, sections and widgets like Glance. The app still owns
                typed records, relations, sources, reversible actions and chat context.
              </Text>
            </View>
            <View style={styles.yamlSnippet}>
              <Text style={styles.code}>home:</Text>
              <Text style={styles.code}>  - now-card</Text>
              <Text style={styles.code}>  - review-queue</Text>
              <Text style={styles.code}>domains:</Text>
              <Text style={styles.code}>  food: gallery + calendar</Text>
            </View>
          </Card>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  content: { alignSelf: 'center', maxWidth: 1080, paddingBottom: 44 },
  topbar: { paddingTop: 16, paddingBottom: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  settingsButton: { minHeight: 40, borderRadius: radius.pill, backgroundColor: colors.ink, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center' },
  settingsButtonText: { color: '#FFF', fontSize: 13, fontWeight: '800' },
  hero: { minHeight: 292, padding: 24, flexDirection: 'row', alignItems: 'center', gap: 24, backgroundColor: '#202416', overflow: 'hidden' },
  heroCompact: { flexDirection: 'column', alignItems: 'stretch' },
  heroCopy: { flex: 1, minWidth: 0 },
  heroTitle: { color: '#FFFDF2', fontSize: 34, lineHeight: 38, fontWeight: '900', letterSpacing: -1.4, marginTop: 18, maxWidth: 650 },
  heroTitleCompact: { maxWidth: 304, fontSize: 28, lineHeight: 32 },
  heroBody: { color: '#D5D8C8', fontSize: 15, lineHeight: 22, marginTop: 12, maxWidth: 650 },
  heroBodyCompact: { maxWidth: 304, fontSize: 14, lineHeight: 21 },
  heroActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 22 },
  primaryAction: { minHeight: 46, borderRadius: radius.pill, backgroundColor: '#FFF1A8', color: colors.ink, paddingHorizontal: 18, paddingVertical: 14, fontSize: 14, fontWeight: '900', overflow: 'hidden' },
  secondaryAction: { minHeight: 46, borderRadius: radius.pill, borderWidth: 1, borderColor: '#FFFFFF55', color: '#FFFDF2', paddingHorizontal: 18, paddingVertical: 14, fontSize: 14, fontWeight: '800', overflow: 'hidden' },
  stackGraphic: { flex: 0.7, minWidth: 0, width: '100%', gap: 0, alignSelf: 'stretch', justifyContent: 'center' },
  stackGraphicCompact: { maxWidth: 304 },
  stackLayer: { minHeight: 54, borderRadius: 18, backgroundColor: '#FFFDF2', borderWidth: 1, borderColor: '#FFFFFF55', paddingHorizontal: 18, justifyContent: 'center' },
  stackLayerText: { color: colors.ink, fontSize: 16, fontWeight: '900' },
  modelCard: { paddingVertical: 4 },
  modelRow: { minHeight: 72, flexDirection: 'row', gap: 14, alignItems: 'center', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  modelRowFirst: { borderTopWidth: 0 },
  modelNumber: { width: 34, color: colors.moss, fontSize: 12, fontWeight: '900' },
  modelCopy: { flex: 1 },
  modelTitle: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  modelDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 3 },
  modelDetailCompact: { maxWidth: 260 },
  domainGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  domainPress: { flexGrow: 1, flexBasis: 240 },
  domainCard: { minHeight: 214, height: '100%' },
  domainTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  domainGlyph: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  domainGlyphText: { color: '#FFF', fontSize: 18, fontWeight: '900' },
  domainTitle: { color: colors.ink, fontSize: 18, fontWeight: '900', marginTop: 20 },
  domainDetail: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 7 },
  domainDetailCompact: { maxWidth: 285 },
  domainLink: { fontSize: 12, fontWeight: '900', marginTop: 'auto', paddingTop: 18 },
  tileGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  tilePress: { flexGrow: 1, flexBasis: 260 },
  controlTile: { minHeight: 150, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, padding: 16 },
  tileKicker: { fontSize: 11, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase' },
  tileDetail: { color: colors.ink, fontSize: 15, lineHeight: 21, fontWeight: '700', marginTop: 12 },
  tileDetailCompact: { maxWidth: 285 },
  tileAction: { color: colors.ink, fontSize: 12, fontWeight: '900', marginTop: 'auto', paddingTop: 14 },
  yamlCard: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginBottom: 14 },
  yamlText: { flex: 1, minWidth: 260 },
  yamlTitle: { color: colors.ink, fontSize: 17, fontWeight: '900' },
  yamlBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 8 },
  yamlSnippet: { minWidth: 230, borderRadius: 16, backgroundColor: '#161A12', padding: 14 },
  code: { color: '#E8F0D7', fontSize: 12, lineHeight: 19, fontFamily: 'monospace' },
  pressed: { opacity: 0.7 },
});
