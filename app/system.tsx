import { Link } from 'expo-router';
import { ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Card, Page, PageHeader, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { colors, radius } from '@/src/theme';
import catalog from '@/packages/domain-config/domain-catalog.v1.json';

type Tone = 'moss' | 'amber' | 'plum' | 'blue';

const domainDetails = {
  Food: {
    icon: '◉',
    tone: 'moss' as Tone,
    description: 'Meals, kitchen, recipes, shopping and spending share one living graph.',
    footnote: '1 domain skill · 3 workflows',
  },
  Health: {
    icon: '♡',
    tone: 'plum' as Tone,
    description: 'Health Connect context is ready when you choose to bring it into LifeOS.',
    footnote: 'Package manifest next',
  },
  Plants: {
    icon: '⌁',
    tone: 'blue' as Tone,
    description: 'A lightweight domain proving that new spaces arrive through config, not new app code.',
    footnote: 'Package manifest next',
  },
} as const;

const automations = [
  {
    icon: '↻',
    title: 'Weekly food reset',
    detail: 'Checks use-soon food, drafts the week and reconciles shopping.',
    meta: 'Sunday · 6:00 PM',
    tone: 'moss' as Tone,
  },
  {
    icon: '⌁',
    title: 'Source steward',
    detail: 'Watches source freshness and keeps citations attached to exact versions.',
    meta: 'Runs after sync',
    tone: 'blue' as Tone,
  },
  {
    icon: '✦',
    title: 'Dinner agent',
    detail: 'Answers from pantry, plans and preferences; acts only on explicit requests.',
    meta: 'On demand',
    tone: 'plum' as Tone,
  },
] as const;

const skills = [
  ['Food brain', 'Inventory, recipes, meals and shopping', '6 tools'],
  ['Weekly reset', 'A durable four-step household workflow', '4 steps'],
  ['Source verifier', 'Checks every quote, version and source link', 'Read only'],
] as const;

const configFiles = [
  ['domain-catalog.v1.json', 'Domains', 'Food active · Health and Plants ready'],
  ['domains/food.v1.json', 'Surfaces', 'Overview, Meals, Kitchen and Shopping'],
  ['schemas/record.v1.schema.json', 'Data', 'Properties, relations, provenance and archive rules'],
  ['food.md', 'Skill', 'Instructions, allowed tools and source boundaries'],
] as const;

const roadmap = [
  ['01', 'Web', 'Now', 'Responsive product shell and interaction proving ground.'],
  ['02', 'Android', 'Next', 'Offline SQLite, native capture, shares and background sync.'],
  ['03', 'iOS', 'Then', 'Same package runtime and surfaces after Android evidence.'],
] as const;

function StatusMark({ tone = 'moss' }: { tone?: Tone }) {
  return <View style={[styles.statusMark, { backgroundColor: toneColor(tone) }]} />;
}

function ResponsiveCard({ children, compact }: { children: ReactNode; compact: boolean }) {
  return <View style={[styles.responsiveCard, compact ? styles.responsiveCardCompact : null]}>{children}</View>;
}

function toneColor(tone: Tone) {
  return {
    moss: colors.moss,
    amber: colors.amber,
    plum: colors.plum,
    blue: colors.blue,
  }[tone];
}

export default function SystemScreen() {
  const { width } = useWindowDimensions();
  const compact = width < 700;
  const visibleDomains = catalog.domains;

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.contextBar}>
            <View>
              <Text style={styles.brand}>LIFEOS / SYSTEM</Text>
              <Text style={styles.context}>Runtime, packages and delivery</Text>
            </View>
            <View style={styles.runtimeBadge}>
              <StatusMark />
              <Text style={styles.runtimeText}>Config valid</Text>
            </View>
          </View>

          <PageHeader
            eyebrow="Config-driven LifeOS"
            title="The operating layer behind your life."
            subtitle="Domains bring the data model. Skills bring judgment. Agents and automations connect them without exposing technical machinery in daily work."
          />

          <Card tone="moss" style={compact ? styles.overviewCardCompact : styles.overviewCard}>
            <View style={styles.overviewCopy}>
              <Pill tone="moss">FOOD PACKAGE LOADED</Pill>
              <Text style={styles.overviewTitle}>One runtime, many life spaces.</Text>
              <Text style={styles.overviewBody}>
                Food is active now. Health and Plants are visible previews; their manifests can join the same shell without rebuilding navigation.
              </Text>
              <View style={styles.summaryRow}>
                <View style={styles.summaryItem}><Text style={styles.summaryValue}>1</Text><Text style={styles.summaryLabel}>active domain</Text></View>
                <View style={styles.summaryItem}><Text style={styles.summaryValue}>2</Text><Text style={styles.summaryLabel}>previews</Text></View>
                <View style={styles.summaryItem}><Text style={styles.summaryValue}>4</Text><Text style={styles.summaryLabel}>data homes</Text></View>
              </View>
            </View>
            <View style={[styles.graphic, compact ? styles.graphicCompact : null]} accessible accessibilityLabel="Connected LifeOS package graph">
              <View style={styles.graphicCore}><Text style={styles.graphicCoreText}>L</Text></View>
              <View style={[styles.graphicNode, styles.graphicNodeOne]}><Text style={styles.graphicNodeText}>Food</Text></View>
              <View style={[styles.graphicNode, styles.graphicNodeTwo]}><Text style={styles.graphicNodeText}>Health</Text></View>
              <View style={[styles.graphicNode, styles.graphicNodeThree]}><Text style={styles.graphicNodeText}>Plants</Text></View>
            </View>
          </Card>

          <SectionTitle title="Domains" />
          <View style={styles.grid}>
            {visibleDomains.map(({ label: name, status }) => {
              const detail = domainDetails[name as keyof typeof domainDetails];
              return (
                <ResponsiveCard key={name} compact={compact}>
                  <Card tone={detail.tone} style={styles.domainCard}>
                    <View style={styles.cardTop}>
                      <View style={[styles.domainIcon, { backgroundColor: toneColor(detail.tone) }]}><Text style={styles.domainIconText}>{detail.icon}</Text></View>
                      <Pill tone={detail.tone}>{status.toUpperCase()}</Pill>
                    </View>
                    <Text style={styles.cardTitle}>{name}</Text>
                    <Text style={styles.cardBody}>{detail.description}</Text>
                    <Text style={styles.cardMeta}>{detail.footnote}</Text>
                  </Card>
                </ResponsiveCard>
              );
            })}
          </View>

          <SectionTitle title="Agents & automations" />
          <Card style={styles.sectionCard}>
            <View style={styles.sectionIntro}>
              <View>
                <Text style={styles.sectionKicker}>BOUNDED BY DESIGN</Text>
                <Text style={styles.sectionLead}>Quiet help, visible outcomes.</Text>
              </View>
              <Pill tone="moss">3 enabled</Pill>
            </View>
            <Text style={styles.sectionDescription}>
              Agents can read allowed sources and run typed, reversible actions. They cannot reveal secrets, make payments or create hidden branches.
            </Text>
            <View style={styles.automationList}>
              {automations.map((automation) => (
                <View key={automation.title} style={styles.automationRow}>
                  <View style={[styles.automationIcon, { backgroundColor: toneColor(automation.tone) }]}><Text style={styles.automationIconText}>{automation.icon}</Text></View>
                  <View style={styles.automationCopy}>
                    <Text style={styles.rowTitle}>{automation.title}</Text>
                    <Text style={styles.rowDetail}>{automation.detail}</Text>
                  </View>
                  <Text style={styles.rowMeta}>{automation.meta}</Text>
                </View>
              ))}
            </View>
          </Card>

          <SectionTitle title="Skills & MCP" />
          <View style={styles.grid}>
            <View style={[styles.wideColumn, compact ? styles.fullColumn : null]}>
              <Card style={styles.sectionCard}>
                <View style={styles.sectionIntro}>
                  <Text style={styles.sectionLead}>Installed skills</Text>
                  <Pill>3 active</Pill>
                </View>
                <View style={styles.skillList}>
                  {skills.map(([name, detail, scope]) => (
                    <View key={name} style={styles.skillRow}>
                      <View style={styles.skillGlyph}><Text style={styles.skillGlyphText}>✦</Text></View>
                      <View style={styles.skillCopy}><Text style={styles.rowTitle}>{name}</Text><Text style={styles.rowDetail}>{detail}</Text></View>
                      <Text style={styles.rowMeta}>{scope}</Text>
                    </View>
                  ))}
                </View>
              </Card>
            </View>
            <View style={[styles.narrowColumn, compact ? styles.fullColumn : null]}>
              <Card tone="plum" style={styles.mcpCard}>
                <Text style={styles.mcpGlyph}>⌘</Text>
                <Pill tone="plum">MCP READY</Pill>
                <Text style={styles.cardTitle}>One tool contract</Text>
                <Text style={styles.cardBody}>Chat, agents and external assistants use the same schemas, sources and reversible actions.</Text>
                <View style={styles.mcpStats}>
                  <Text style={styles.mcpStat}>6 tools</Text>
                  <Text style={styles.mcpStat}>5 resources</Text>
                  <Text style={styles.mcpStat}>Read + reversible</Text>
                </View>
              </Card>
            </View>
          </View>

          <SectionTitle title="Schemas & config" />
          <Card style={styles.filesCard}>
            <View style={styles.filesHeader}>
              <View>
                <Text style={styles.sectionLead}>Human-readable system files</Text>
                <Text style={styles.rowDetail}>Versioned, validated and free of credentials.</Text>
              </View>
              <Pill tone="blue">4 loaded</Pill>
            </View>
            {configFiles.map(([file, kind, description]) => (
              <View key={file} style={styles.fileRow}>
                <View style={styles.fileIcon}><Text style={styles.fileIconText}>{'{ }'}</Text></View>
                <View style={styles.fileCopy}>
                  <Text style={styles.fileName}>{file}</Text>
                  <Text style={styles.rowDetail}>{description}</Text>
                </View>
                <Text style={styles.fileKind}>{kind}</Text>
              </View>
            ))}
          </Card>

          <SectionTitle title="Delivery roadmap" />
          <Card style={styles.roadmapCard}>
            {roadmap.map(([number, platform, status, detail], index) => (
              <View key={platform} style={styles.roadmapRow}>
                <View style={styles.roadmapRail}>
                  <View style={[styles.roadmapNumber, index === 0 ? styles.roadmapNumberActive : null]}><Text style={[styles.roadmapNumberText, index === 0 ? styles.roadmapNumberTextActive : null]}>{number}</Text></View>
                  {index < roadmap.length - 1 ? <View style={styles.roadmapLine} /> : null}
                </View>
                <View style={styles.roadmapCopy}>
                  <View style={styles.roadmapTitleRow}><Text style={styles.roadmapTitle}>{platform}</Text><Pill tone={index === 0 ? 'moss' : index === 1 ? 'blue' : 'neutral'}>{status.toUpperCase()}</Pill></View>
                  <Text style={styles.roadmapDetail}>{detail}</Text>
                </View>
              </View>
            ))}
          </Card>

          <Link href="/sources" asChild>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.sourceAction, pressed ? styles.pressed : null]}>
              <View>
                <Text style={styles.sourceActionEyebrow}>DATA PLANE</Text>
                <Text style={styles.sourceActionTitle}>Inspect sources & sync</Text>
                <Text style={styles.sourceActionBody}>See authority, freshness, citation coverage and provider readiness.</Text>
              </View>
              <Text style={styles.sourceActionArrow}>→</Text>
            </Pressable>
          </Link>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.6 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  runtimeBadge: { minHeight: 36, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, backgroundColor: colors.paper, paddingHorizontal: 12 },
  statusMark: { width: 7, height: 7, borderRadius: 4 },
  runtimeText: { color: colors.ink, fontSize: 11, fontWeight: '800' },
  overviewCard: { minHeight: 238, padding: 24, flexDirection: 'row', alignItems: 'center', overflow: 'hidden' },
  overviewCardCompact: { minHeight: 386, padding: 20, alignItems: 'stretch', overflow: 'hidden' },
  overviewCopy: { flex: 1, minWidth: 230, zIndex: 1 },
  overviewTitle: { color: colors.ink, fontSize: 26, lineHeight: 31, fontWeight: '800', letterSpacing: -0.8, marginTop: 18 },
  overviewBody: { color: colors.muted, fontSize: 14, lineHeight: 21, maxWidth: 570, marginTop: 8 },
  summaryRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 24, marginTop: 24 },
  summaryItem: { gap: 2 },
  summaryValue: { color: colors.ink, fontSize: 24, lineHeight: 27, fontWeight: '800' },
  summaryLabel: { color: colors.muted, fontSize: 11, fontWeight: '700' },
  graphic: { width: 210, height: 190, alignItems: 'center', justifyContent: 'center', marginRight: -8 },
  graphicCompact: { width: '100%', height: 150, marginTop: 12, marginRight: 0 },
  graphicCore: { width: 76, height: 76, borderRadius: 38, backgroundColor: colors.ink, alignItems: 'center', justifyContent: 'center' },
  graphicCoreText: { color: '#FFF', fontSize: 28, fontWeight: '900' },
  graphicNode: { position: 'absolute', minWidth: 58, minHeight: 34, paddingHorizontal: 10, borderRadius: radius.pill, backgroundColor: colors.paper, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center' },
  graphicNodeOne: { top: 8, left: 8 },
  graphicNodeTwo: { top: 26, right: 0 },
  graphicNodeThree: { bottom: 8, right: 18 },
  graphicNodeText: { color: colors.ink, fontSize: 11, fontWeight: '800' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  responsiveCard: { minWidth: 220, flexGrow: 1, flexBasis: 0 },
  responsiveCardCompact: { flexBasis: '100%' },
  domainCard: { minHeight: 226, height: '100%' },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  domainIcon: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  domainIconText: { color: '#FFF', fontSize: 21, fontWeight: '800' },
  cardTitle: { color: colors.ink, fontSize: 18, fontWeight: '800', marginTop: 20 },
  cardBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 7 },
  cardMeta: { color: colors.ink, fontSize: 11, lineHeight: 16, fontWeight: '800', marginTop: 'auto', paddingTop: 18 },
  sectionCard: { padding: 20 },
  sectionIntro: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  sectionKicker: { color: colors.moss, fontSize: 10, fontWeight: '900', letterSpacing: 1.2 },
  sectionLead: { color: colors.ink, fontSize: 17, fontWeight: '800', marginTop: 4 },
  sectionDescription: { color: colors.muted, fontSize: 13, lineHeight: 20, maxWidth: 720, marginTop: 10 },
  automationList: { marginTop: 18 },
  automationRow: { minHeight: 78, flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  automationIcon: { width: 38, height: 38, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  automationIconText: { color: '#FFF', fontSize: 18, fontWeight: '800' },
  automationCopy: { flex: 1 },
  rowTitle: { color: colors.ink, fontSize: 14, fontWeight: '800' },
  rowDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 3 },
  rowMeta: { color: colors.muted, fontSize: 11, fontWeight: '700', textAlign: 'right', maxWidth: 112 },
  wideColumn: { flexGrow: 1, flexBasis: 520, minWidth: 280 },
  narrowColumn: { flexGrow: 1, flexBasis: 280, minWidth: 240 },
  fullColumn: { flexBasis: '100%' },
  skillList: { marginTop: 12 },
  skillRow: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  skillGlyph: { width: 34, height: 34, borderRadius: 11, backgroundColor: colors.mossSoft, alignItems: 'center', justifyContent: 'center' },
  skillGlyphText: { color: colors.moss, fontSize: 16, fontWeight: '800' },
  skillCopy: { flex: 1 },
  mcpCard: { minHeight: 262, height: '100%', padding: 20 },
  mcpGlyph: { color: colors.plum, fontSize: 34, lineHeight: 40, marginBottom: 12 },
  mcpStats: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginTop: 'auto', paddingTop: 22 },
  mcpStat: { color: colors.plum, backgroundColor: '#FFF9', borderRadius: radius.pill, paddingHorizontal: 9, paddingVertical: 6, fontSize: 10, fontWeight: '800' },
  filesCard: { paddingVertical: 8 },
  filesHeader: { paddingHorizontal: 4, paddingVertical: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  fileRow: { minHeight: 76, flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  fileIcon: { width: 40, height: 40, borderRadius: 12, backgroundColor: '#EEEDE5', alignItems: 'center', justifyContent: 'center' },
  fileIconText: { color: colors.blue, fontSize: 11, fontWeight: '900' },
  fileCopy: { flex: 1 },
  fileName: { color: colors.ink, fontSize: 13, fontWeight: '800', fontFamily: 'monospace' },
  fileKind: { color: colors.muted, fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 0.8 },
  roadmapCard: { paddingHorizontal: 20, paddingVertical: 12 },
  roadmapRow: { flexDirection: 'row', gap: 16, minHeight: 102 },
  roadmapRail: { width: 40, alignItems: 'center' },
  roadmapNumber: { width: 38, height: 38, borderRadius: 19, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, alignItems: 'center', justifyContent: 'center' },
  roadmapNumberActive: { backgroundColor: colors.ink, borderColor: colors.ink },
  roadmapNumberText: { color: colors.muted, fontSize: 11, fontWeight: '900' },
  roadmapNumberTextActive: { color: '#FFF' },
  roadmapLine: { width: 1, flex: 1, backgroundColor: colors.line },
  roadmapCopy: { flex: 1, paddingBottom: 24 },
  roadmapTitleRow: { minHeight: 38, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  roadmapTitle: { color: colors.ink, fontSize: 16, fontWeight: '800' },
  roadmapDetail: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 7, maxWidth: 640 },
  sourceAction: { minHeight: 112, marginTop: 24, marginBottom: 12, borderRadius: radius.md, backgroundColor: colors.ink, padding: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 18 },
  sourceActionEyebrow: { color: '#AFC19F', fontSize: 10, fontWeight: '900', letterSpacing: 1.1 },
  sourceActionTitle: { color: '#FFF', fontSize: 18, fontWeight: '800', marginTop: 5 },
  sourceActionBody: { color: '#C8CCC3', fontSize: 12, lineHeight: 17, marginTop: 4, maxWidth: 620 },
  sourceActionArrow: { color: '#FFF', fontSize: 28, fontWeight: '400' },
  pressed: { opacity: 0.72 },
});
