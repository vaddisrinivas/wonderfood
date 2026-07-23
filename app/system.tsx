import { Link } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Card, Page, Pill, SectionTitle } from '@/src/components/ui';
import { colors, radius, useLifeOSTheme } from '@/src/theme';
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
  ['Sources', 'Notion, Sheets, local graph and pull results.', 'Open data homes', '/sources', 'blue'],
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

function toneSoft(tone: Tone, palette = colors) {
  return { moss: palette.mossSoft, amber: palette.amberSoft, plum: palette.plumSoft, blue: palette.blueSoft }[tone];
}

function toneInk(tone: Tone, palette = colors) {
  return { moss: palette.moss, amber: palette.amber, plum: palette.plum, blue: palette.blue }[tone];
}

export default function SystemScreen() {
  const theme = useLifeOSTheme();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const contentWidth = compact ? Math.max(width - 36, 280) : '100%';

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={[styles.content, { width: contentWidth }]}>
          <View style={styles.topbar}>
            <View>
              <Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / CONTROL DECK</Text>
              <Text style={[styles.context, { color: theme.colors.muted }]}>Advanced settings, not your daily home</Text>
            </View>
            <Link href="/settings" style={[styles.settingsLink, { backgroundColor: theme.colors.ink, color: theme.colors.paper }]}>Settings</Link>
          </View>

          <Card style={[styles.hero, { backgroundColor: theme.colors.ink, borderColor: theme.colors.line }, compact && styles.heroCompact]}>
            <View style={styles.heroCopy}>
              <Pill tone="moss">PORTABLE LIFEOS</Pill>
              <Text style={[styles.heroTitle, { color: theme.colors.paper }, compact && styles.heroTitleCompact]}>Control the machinery without living in it.</Text>
              <Text style={[styles.heroBody, { color: theme.colors.mossSoft }, compact && styles.heroBodyCompact]}>
                Your app should feel like Notion plus GPT, not a status page. Daily work belongs on Home and Domain pages.
                This deck is only for changing sources, domains, skills, schemas, providers and sync.
              </Text>
              <View style={styles.heroActions}>
                <Link href="/" style={[styles.primaryAction, { backgroundColor: theme.colors.amberSoft, color: theme.colors.ink }]}>Open Home</Link>
                <Link href="/(tabs)/food" style={[styles.secondaryAction, { borderColor: theme.colors.line, color: theme.colors.paper }]}>Open Food</Link>
              </View>
            </View>
            <View style={[styles.stackGraphic, compact && styles.stackGraphicCompact]} accessible accessibilityLabel="LifeOS stack: Home, Domain, Chat, Sources and Settings">
              {homeModel.map(([name], index) => (
                <View key={name} style={[styles.stackLayer, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line, transform: [{ translateY: index * -6 }] }]}>
                  <Text style={[styles.stackLayerText, { color: theme.colors.ink }]}>{name}</Text>
                </View>
              ))}
            </View>
          </Card>

          <SectionTitle title="Screen model" />
          <Card style={styles.modelCard}>
            {homeModel.map(([name, detail], index) => (
              <View key={name} style={[styles.modelRow, { borderTopColor: theme.colors.line }, index === 0 && styles.modelRowFirst]}>
                <Text style={[styles.modelNumber, { color: theme.colors.moss }]}>{String(index + 1).padStart(2, '0')}</Text>
                <View style={styles.modelCopy}>
                  <Text style={[styles.modelTitle, { color: theme.colors.ink }]}>{name}</Text>
                  <Text style={[styles.modelDetail, { color: theme.colors.muted }, compact && styles.modelDetailCompact]}>{detail}</Text>
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
                        <View style={[styles.domainGlyph, { backgroundColor: toneInk(domain.tone, theme.colors) }]}>
                          <Text style={[styles.domainGlyphText, { color: theme.colors.paper }]}>{domain.glyph}</Text>
                        </View>
                        <Pill tone={domain.tone}>{status.toUpperCase()}</Pill>
                      </View>
                      <Text style={[styles.domainTitle, { color: theme.colors.ink }]}>{domain.title}</Text>
                      <Text style={[styles.domainDetail, { color: theme.colors.muted }, compact && styles.domainDetailCompact]}>{domain.detail}</Text>
                      <Text style={[styles.domainLink, { color: toneInk(domain.tone, theme.colors) }]}>Open or configure →</Text>
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
                  <View style={[styles.controlTile, { backgroundColor: toneSoft(tone as Tone, theme.colors), borderColor: theme.colors.line }]}>
                    <Text style={[styles.tileKicker, { color: toneInk(tone as Tone, theme.colors) }]}>{title}</Text>
                    <Text style={[styles.tileDetail, { color: theme.colors.ink }, compact && styles.tileDetailCompact]}>{detail}</Text>
                    <Text style={[styles.tileAction, { color: theme.colors.ink }]}>{action} →</Text>
                  </View>
                </Pressable>
              </Link>
            ))}
          </View>

          <SectionTitle title="Glance-style config, LifeOS-grade model" />
          <Card style={styles.yamlCard}>
            <View style={styles.yamlText}>
              <Text style={[styles.yamlTitle, { color: theme.colors.ink }]}>Borrow the YAML idea. Do not become YAML-first.</Text>
              <Text style={[styles.yamlBody, { color: theme.colors.muted }]}>
                A portable LifeOS profile can describe pages, sections and widgets like Glance. The app still owns
                typed records, relations, sources, reversible actions and chat context.
              </Text>
            </View>
            <View style={[styles.yamlSnippet, { backgroundColor: theme.colors.ink }]}>
              <Text style={[styles.code, { color: theme.colors.mossSoft }]}>home:</Text>
              <Text style={[styles.code, { color: theme.colors.mossSoft }]}>  - now-card</Text>
              <Text style={[styles.code, { color: theme.colors.mossSoft }]}>  - review-queue</Text>
              <Text style={[styles.code, { color: theme.colors.mossSoft }]}>domains:</Text>
              <Text style={[styles.code, { color: theme.colors.mossSoft }]}>  food: gallery + calendar</Text>
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
  settingsLink: { minHeight: 40, borderRadius: radius.pill, backgroundColor: colors.ink, color: '#FFF', paddingHorizontal: 16, paddingVertical: 12, fontSize: 13, fontWeight: '800', overflow: 'hidden' },
  hero: { minHeight: 292, padding: 24, flexDirection: 'row', alignItems: 'center', gap: 24, backgroundColor: '#202416', overflow: 'hidden' },
  heroCompact: { flexDirection: 'column', alignItems: 'stretch' },
  heroCopy: { flex: 1, minWidth: 0 },
  heroTitle: { color: '#FFFDF2', fontSize: 34, lineHeight: 38, fontWeight: '900', letterSpacing: -1.1, marginTop: 18, maxWidth: 650 },
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
