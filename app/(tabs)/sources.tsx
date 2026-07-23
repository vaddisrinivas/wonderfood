import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Card, Page, PageHeader, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { listSourceRows } from '@/src/domain/queries';
import { useLifeOSDatabase } from '@/src/db/provider';
import { loadCatalog } from '@/src/domain/catalog';
import { DirectSyncReceipt, syncConfiguredSources, syncNotionDirect, syncSheetsDirect } from '@/src/providers/direct-source-sync';
import { LifeOSSettings, defaultLifeOSSettings, loadLifeOSSettings } from '@/src/settings/lifeos-settings';
import { colors, radius } from '@/src/theme';

type SourceRow = {
  name: string;
  status: string;
  freshness: string;
  workspace: string | null;
};

type Tone = 'moss' | 'amber' | 'plum' | 'blue';

const sourceMeta: Record<string, { icon: string; tone: Tone; role: string; summary: string; scope: string; action: string; href: string | null; }> = {
  notion: {
    icon: 'N',
    tone: 'moss' as Tone,
    role: 'Optional authority',
    summary: 'Connect your own Notion workspace for meals, kitchen, recipes and planning.',
    scope: 'User configured',
    action: 'Configure provider',
    href: null,
  },
  'google_sheets': {
    icon: '▦',
    tone: 'blue' as Tone,
    role: 'Optional surface',
    summary: 'Connect your own workbook for auditable rows, formulas and collaboration.',
    scope: 'User configured',
    action: 'Configure provider',
    href: null,
  },
  sqlite: {
    icon: '▣',
    tone: 'plum' as Tone,
    role: 'Planned device replica',
    summary: 'Fast offline graph, outbox, source snapshots and recovery.',
    scope: 'Encrypted · on device',
    action: 'Adapter implementation next',
    href: null,
  },
  postgres: {
    icon: 'P',
    tone: 'amber' as Tone,
    role: 'Ready',
    summary: 'Hosted authority option for household scale and durable history.',
    scope: 'Not connected',
    action: 'Connection guide planned',
    href: null,
  },
} as const;

function normalizeSourceName(name: string): keyof typeof sourceMeta | 'other' {
  const normalized = name.toLowerCase().replace(/\s+/g, '_');
  if (normalized in sourceMeta) return normalized as keyof typeof sourceMeta;
  return 'other';
}

const citationSources = [
  ['[LifeOS Notion]', 'Pages, properties, relations and exact block quotes', 'When connected'],
  ['[LifeOS Sheets]', 'Rows, cells, formulas and workbook timestamps', 'When connected'],
  ['[App snapshot]', 'Local kitchen, plans, shopping and recent actions', 'Demo'],
  ['[MCP schema]', 'Domain catalog, skill rules and tool contracts', 'Versioned'],
] as const;

const recentSync = [
  ['01', 'Notion direct pull', 'Reads user data sources into canonical records from in-app credentials'],
  ['02', 'Sheets direct pull', 'Reads workbook rows into the same canonical graph from in-app credentials'],
  ['03', 'SQLite replica', 'Persists records, source snapshots and chat citations on device'],
] as const;

function toneColor(tone: Tone) {
  return {
    moss: colors.moss,
    amber: colors.amber,
    plum: colors.plum,
    blue: colors.blue,
  }[tone];
}

export default function SourcesScreen() {
  const { width } = useWindowDimensions();
  const compact = width < 720;
  const db = useLifeOSDatabase();
  const { activeManifest } = loadCatalog();
  const [sourceRows, setSourceRows] = useState<SourceRow[]>([]);
  const [settings, setSettings] = useState<LifeOSSettings>(defaultLifeOSSettings);
  const [receipts, setReceipts] = useState<DirectSyncReceipt[]>([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState<DirectSyncReceipt['provider'] | 'all' | null>(null);
  const hasDb = Boolean(db);

  const refreshRows = async () => {
    setLoading(true);
    const [rows, loadedSettings] = await Promise.all([listSourceRows(db), loadLifeOSSettings()]);
    setSettings(loadedSettings);
    setSourceRows(mergeConfiguredRows(rows, loadedSettings));
    setLoading(false);
  };

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      const [rows, loadedSettings] = await Promise.all([listSourceRows(db), loadLifeOSSettings()]);
      if (!cancelled) {
        setSettings(loadedSettings);
        setSourceRows(mergeConfiguredRows(rows, loadedSettings));
        setLoading(false);
      }
    };
    setLoading(true);
    void run();
    return () => {
      cancelled = true;
    };
  }, [db]);

  const runSync = async (target: DirectSyncReceipt['provider'] | 'all') => {
    setSyncing(target);
    const loadedSettings = await loadLifeOSSettings();
    setSettings(loadedSettings);
    const result = target === 'all'
      ? await syncConfiguredSources({ db, manifest: activeManifest, settings: loadedSettings })
      : target === 'notion'
        ? [await syncNotionDirect({ db, manifest: activeManifest, settings: loadedSettings })]
        : [await syncSheetsDirect({ db, manifest: activeManifest, settings: loadedSettings })];
    setReceipts(result);
    await refreshRows();
    setSyncing(null);
  };

  const configuredCount = [settings.notion.enabled, settings.sheets.enabled, settings.postgres.enabled, settings.mcp.enabled].filter(Boolean).length;
  const latestReceipt = receipts[0];

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.contextBar}>
            <View>
              <Text style={styles.brand}>LIFEOS / SOURCES</Text>
              <Text style={styles.context}>Authority, sync and citation health</Text>
            </View>
            <View style={styles.liveBadge}><View style={styles.liveDot} /><Text style={styles.liveText}>{latestReceipt ? latestReceipt.status : 'Local ready'}</Text></View>
          </View>

          <PageHeader
            eyebrow="One graph · Your chosen homes"
            title="Your data stays legible."
            subtitle="LifeOS keeps one clear authority, an offline device replica and source versions that Chat can quote. Provider details never leak into daily work."
          />

          <Card tone="blue" style={[styles.loopCard, compact ? styles.loopCardCompact : null]}>
            <View style={[styles.loopCopy, compact ? styles.loopCopyCompact : null]}>
              <Pill tone="blue">DIRECT SYNC READY</Pill>
              <Text style={styles.loopTitle}>Pull your sources into one local graph.</Text>
              <Text style={styles.loopBody}>
                Start locally, then pull your own Notion data sources or Sheets workbook without webhooks or a mandatory LifeOS server.
              </Text>
              <View style={styles.syncActions}>
                <Pressable accessibilityRole="button" disabled={Boolean(syncing)} onPress={() => void runSync('all')} style={({ pressed }) => [styles.primarySync, syncing && styles.disabled, pressed && styles.pressed]}>
                  <Text style={styles.primarySyncText}>{syncing === 'all' ? 'Syncing...' : 'Sync enabled sources'}</Text>
                </Pressable>
                <Link href="/settings" asChild>
                  <Pressable accessibilityRole="button" style={({ pressed }) => [styles.secondarySync, pressed && styles.pressed]}>
                    <Text style={styles.secondarySyncText}>Connections</Text>
                  </Pressable>
                </Link>
              </View>
            </View>
            <View style={[styles.loopFlow, compact ? styles.loopFlowCompact : null]} accessibilityLabel="Notion syncs through the LifeOS graph to SQLite and Google Sheets">
              <View style={styles.flowNode}><Text style={styles.flowNodeLabel}>Your source</Text><Text style={styles.flowNodeRole}>Optional</Text></View>
              <Text style={styles.flowArrow}>→</Text>
              <View style={[styles.flowNode, styles.flowNodeCore]}><Text style={styles.flowNodeCoreLabel}>LifeOS</Text><Text style={styles.flowNodeCoreRole}>Graph</Text></View>
              <Text style={styles.flowArrow}>→</Text>
              <View style={styles.flowDestinations}>
                <Text style={styles.flowDestination}>SQLite · local</Text>
                <Text style={styles.flowDestination}>Providers · optional</Text>
              </View>
            </View>
          </Card>

          <View style={styles.metrics}>
            <Card style={styles.metric}><Text style={styles.metricValue}>{loading ? '...' : `${sourceRows.length}`}</Text><Text style={styles.metricLabel}>sources known</Text><Text style={styles.metricFoot}>Configured homes and links</Text></Card>
            <Card style={styles.metric}><Text style={styles.metricValue}>{configuredCount}</Text><Text style={styles.metricLabel}>enabled settings</Text><Text style={styles.metricFoot}>Editable in app</Text></Card>
            <Card style={styles.metric}><Text style={styles.metricValue}>{latestReceipt ? `${latestReceipt.records}` : '0'}</Text><Text style={styles.metricLabel}>last pull rows</Text><Text style={styles.metricFoot}>{latestReceipt?.message ?? 'No sync run this session'}</Text></Card>
          </View>

          {receipts.length ? (
            <Card style={styles.receiptCard}>
              <Text style={styles.sectionLead}>Latest sync receipts</Text>
              <View style={styles.receiptList}>
                {receipts.map((receipt) => (
                  <View key={`${receipt.provider}-${receipt.observedAt}`} style={styles.receiptRow}>
                    <Pill tone={receipt.status === 'synced' ? 'moss' : receipt.status === 'blocked' ? 'blue' : 'amber'}>{receipt.status.toUpperCase()}</Pill>
                    <View style={styles.receiptCopy}>
                      <Text style={styles.receiptTitle}>{receipt.provider.replace('_', ' ')}</Text>
                      <Text style={styles.receiptMessage}>{receipt.message}</Text>
                    </View>
                    <Text style={styles.receiptCount}>{receipt.records} rows</Text>
                  </View>
                ))}
              </View>
            </Card>
          ) : null}

          <SectionTitle title="Data homes & surfaces" />
          {!sourceRows.length ? (
            <Card>
              <Text style={styles.emptyTitle}>No sources connected in this session</Text>
              <Text style={styles.emptyBody}>
                {hasDb
                  ? 'Adapter links are not loaded yet. Connect Notion and/or Sheets in Settings to begin sync capture.'
                  : 'LifeOS loaded without a local graph yet. Add your first authority in Settings.'}
              </Text>
              <Link href="/settings" asChild>
                <Pressable style={styles.openSystem}>
                  <Text style={styles.openSystemText}>Open Settings</Text>
                  <Text style={styles.openSystemArrow}>→</Text>
                </Pressable>
              </Link>
            </Card>
          ) : null}

          <View style={styles.sourceGrid}>
            {sourceRows.map((sourceRow) => {
              const normalized = normalizeSourceName(sourceRow.name);
              const meta = normalized === 'other'
                ? { icon: '◉', tone: 'blue' as Tone, role: 'Unknown source', summary: `${sourceRow.name} source discovered at runtime.`, scope: sourceRow.freshness, action: 'Inspect source', href: null }
                : sourceMeta[normalized];
              const isReady = normalized === 'postgres';
              return (
                <View key={sourceRow.name} style={[styles.sourceCell, compact ? styles.sourceCellCompact : null]}>
                  <Card style={styles.sourceCard}>
                    <View style={styles.sourceTop}>
                      <View style={[styles.sourceIcon, { backgroundColor: toneColor(meta.tone) }]}><Text style={styles.sourceIconText}>{meta.icon}</Text></View>
                      <Pill tone={isReady ? 'amber' : meta.tone}>{sourceRow.status.toUpperCase()}</Pill>
                    </View>
                    <Text style={styles.sourceName}>{sourceRow.name}</Text>
                    <Text style={styles.sourceRole}>{meta.role} · {sourceRow.workspace}</Text>
                    <Text style={styles.sourceSummary}>{meta.summary}</Text>
                    <View style={styles.sourceFacts}>
                      <View><Text style={styles.factLabel}>FRESHNESS</Text><Text style={styles.factValue}>{sourceRow.freshness}</Text></View>
                      <View><Text style={styles.factLabel}>SCOPE</Text><Text style={styles.factValue}>{meta.scope}</Text></View>
                    </View>
                    <Pressable
                      accessibilityRole={meta.href ? 'link' : 'button'}
                      disabled={Boolean(syncing)}
                      onPress={() => {
                        if (normalized === 'notion') void runSync('notion');
                        else if (normalized === 'google_sheets') void runSync('google_sheets');
                        else if (meta.href) void Linking.openURL(meta.href);
                      }}
                      style={styles.sourceActionRow}
                    >
                      <Text style={styles.sourceAction}>{normalized === 'notion' || normalized === 'google_sheets' ? 'Pull now' : meta.action}</Text>
                      <Text style={styles.sourceActionArrow}>{syncing === normalized ? '...' : meta.href ? '↗' : '→'}</Text>
                    </Pressable>
                  </Card>
                </View>
              );
            })}
          </View>

          <SectionTitle title="What Chat can cite" />
          <Card style={styles.citationCard}>
            <View style={styles.citationHeader}>
              <View>
                <Text style={styles.sectionLead}>Source-backed by default</Text>
                <Text style={styles.sectionDetail}>Every household claim opens the exact record, quote and observed version.</Text>
              </View>
              <Pill tone="moss">4 source packs</Pill>
            </View>
            <View style={styles.citationList}>
              {citationSources.map(([handle, scope, freshness]) => (
                <View key={handle} style={styles.citationRow}>
                  <View style={styles.quoteMark}><Text style={styles.quoteMarkText}>“</Text></View>
                  <View style={styles.citationCopy}>
                    <Text style={styles.citationHandle}>{handle}</Text>
                    <Text style={styles.citationScope}>{scope}</Text>
                  </View>
                  <Text style={styles.citationFreshness}>{freshness}</Text>
                </View>
              ))}
            </View>
          </Card>

          <View style={styles.detailGrid}>
            <View style={[styles.detailMain, compact ? styles.detailFull : null]}>
              <SectionTitle title="Sync implementation order" />
              <Card style={styles.timelineCard}>
                {recentSync.map(([time, title, detail], index) => (
                  <View key={`${time}-${title}`} style={styles.timelineRow}>
                    <View style={styles.timelineRail}>
                      <View style={[styles.timelineDot, index === 0 ? styles.timelineDotActive : null]} />
                      {index < recentSync.length - 1 ? <View style={styles.timelineLine} /> : null}
                    </View>
                    <View style={styles.timelineCopy}>
                      <View style={styles.timelineTitleRow}><Text style={styles.timelineTitle}>{title}</Text><Text style={styles.timelineTime}>STEP {time}</Text></View>
                      <Text style={styles.timelineDetail}>{detail}</Text>
                    </View>
                  </View>
                ))}
              </Card>
            </View>

            <View style={[styles.detailSide, compact ? styles.detailFull : null]}>
              <SectionTitle title="Sync policy" />
              <Card tone="moss" style={styles.policyCard}>
                <Pill tone="moss">CALM BY DEFAULT</Pill>
                <Text style={styles.policyTitle}>One authority. No sync loops.</Text>
                <View style={styles.policyList}>
                  <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>SQLite remains available offline.</Text></View>
                  <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Exact source versions stay attached to answers.</Text></View>
                  <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Unknown provider fields are preserved.</Text></View>
                  <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Credentials never appear here.</Text></View>
                </View>
              </Card>
            </View>
          </View>

          <Link href="/config" asChild>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.systemAction, pressed ? styles.pressed : null]}>
              <View>
                <Text style={styles.systemActionEyebrow}>CONFIG MAP</Text>
                <Text style={styles.systemActionTitle}>See domains, skills and agents</Text>
                <Text style={styles.systemActionBody}>Edit the packages and schemas that decide how sources become LifeOS pages.</Text>
              </View>
              <Text style={styles.systemActionArrow}>→</Text>
            </Pressable>
          </Link>
        </View>
      </ScrollView>
    </Page>
  );
}

function mergeConfiguredRows(rows: SourceRow[], settings: LifeOSSettings): SourceRow[] {
  const byName = new Map(rows.map((row) => [row.name.toLowerCase(), row]));
  const next = [...rows];
  if (settings.notion.enabled && !byName.has('notion')) {
    next.push({
      name: 'notion',
      status: settings.notion.token && settings.notion.dataSourceIds ? 'Configured' : 'Needs setup',
      freshness: 'Not pulled yet',
      workspace: settings.notion.pageId || settings.notion.dataSourceIds || 'Notion',
    });
  }
  if (settings.sheets.enabled && !byName.has('google_sheets')) {
    next.push({
      name: 'google_sheets',
      status: settings.sheets.token && settings.sheets.workbookId ? 'Configured' : 'Needs setup',
      freshness: 'Not pulled yet',
      workspace: settings.sheets.sheetName || 'LifeOS Canonical',
    });
  }
  if (settings.postgres.enabled && !byName.has('postgres')) {
    next.push({ name: 'postgres', status: 'Configured', freshness: 'Not pulled yet', workspace: 'Postgres' });
  }
  return next;
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  brand: { color: colors.blue, fontSize: 12, fontWeight: '900', letterSpacing: 1.6 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  liveBadge: { minHeight: 36, flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.paper, borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, paddingHorizontal: 12 },
  liveDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: colors.moss },
  liveText: { color: colors.ink, fontSize: 11, fontWeight: '800' },
  loopCard: { minHeight: 224, padding: 24, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 28 },
  loopCardCompact: { padding: 20, alignItems: 'stretch', gap: 20 },
  loopCopy: { flexGrow: 1, flexBasis: 360, minWidth: 240 },
  loopCopyCompact: { flexBasis: '100%', minWidth: 0, width: '100%' },
  loopTitle: { color: colors.ink, fontSize: 25, lineHeight: 30, fontWeight: '800', letterSpacing: -0.7, marginTop: 18 },
  loopBody: { color: colors.muted, fontSize: 14, lineHeight: 21, marginTop: 8, maxWidth: 560 },
  syncActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 20 },
  primarySync: { minHeight: 48, borderRadius: radius.pill, backgroundColor: colors.ink, paddingHorizontal: 18, alignItems: 'center', justifyContent: 'center' },
  primarySyncText: { color: '#FFF', fontSize: 12, fontWeight: '900' },
  secondarySync: { minHeight: 48, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, paddingHorizontal: 18, alignItems: 'center', justifyContent: 'center' },
  secondarySyncText: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  loopFlow: { flexGrow: 1, flexBasis: 380, minWidth: 320, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10 },
  loopFlowCompact: { minWidth: 0, flexBasis: '100%', justifyContent: 'flex-start', flexWrap: 'wrap' },
  flowNode: { minWidth: 82, minHeight: 60, borderRadius: radius.md, backgroundColor: colors.paper, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center', padding: 10 },
  flowNodeCore: { backgroundColor: colors.ink, borderColor: colors.ink },
  flowNodeLabel: { color: colors.ink, fontSize: 13, fontWeight: '800' },
  flowNodeRole: { color: colors.muted, fontSize: 9, fontWeight: '800', textTransform: 'uppercase', marginTop: 4 },
  flowNodeCoreLabel: { color: '#FFF', fontSize: 13, fontWeight: '800' },
  flowNodeCoreRole: { color: '#BAC2B6', fontSize: 9, fontWeight: '800', textTransform: 'uppercase', marginTop: 4 },
  flowArrow: { color: colors.blue, fontSize: 20 },
  flowDestinations: { gap: 6 },
  flowDestination: { color: colors.blue, backgroundColor: '#FFFC', borderRadius: radius.pill, paddingHorizontal: 10, paddingVertical: 7, fontSize: 10, fontWeight: '800' },
  metrics: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginTop: 12 },
  metric: { minWidth: 150, flexGrow: 1, flexBasis: 0 },
  metricValue: { color: colors.ink, fontSize: 25, fontWeight: '800', letterSpacing: -0.7 },
  metricLabel: { color: colors.ink, fontSize: 13, fontWeight: '800', marginTop: 4 },
  metricFoot: { color: colors.muted, fontSize: 11, marginTop: 4 },
  receiptCard: { marginTop: 12, padding: 18 },
  receiptList: { marginTop: 12 },
  receiptRow: { minHeight: 70, flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  receiptCopy: { flex: 1, minWidth: 0 },
  receiptTitle: { color: colors.ink, fontSize: 13, fontWeight: '800', textTransform: 'capitalize' },
  receiptMessage: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 3 },
  receiptCount: { color: colors.moss, fontSize: 11, fontWeight: '900' },
  sourceGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  sourceCell: { minWidth: 280, flexGrow: 1, flexBasis: '47%' },
  sourceCellCompact: { flexBasis: '100%', minWidth: 0 },
  sourceCard: { minHeight: 270, height: '100%', padding: 20 },
  sourceTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  sourceIcon: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  sourceIconText: { color: '#FFF', fontSize: 18, fontWeight: '900' },
  sourceName: { color: colors.ink, fontSize: 19, fontWeight: '800', marginTop: 18 },
  sourceRole: { color: colors.moss, fontSize: 11, fontWeight: '800', marginTop: 4 },
  sourceSummary: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 12, maxWidth: 460 },
  sourceFacts: { flexDirection: 'row', flexWrap: 'wrap', gap: 24, marginTop: 20 },
  factLabel: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 0.9 },
  factValue: { color: colors.ink, fontSize: 11, fontWeight: '800', marginTop: 4 },
  sourceActionRow: { marginTop: 'auto', paddingTop: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  sourceAction: { color: colors.ink, fontSize: 12, fontWeight: '800' },
  sourceActionArrow: { color: colors.muted, fontSize: 17, fontWeight: '800' },
  sourceStateDot: { width: 7, height: 7, borderRadius: 4 },
  citationCard: { padding: 20 },
  citationHeader: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  sectionLead: { color: colors.ink, fontSize: 17, fontWeight: '800' },
  sectionDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 4, maxWidth: 630 },
  citationList: { marginTop: 16 },
  citationRow: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  quoteMark: { width: 36, height: 36, borderRadius: 12, backgroundColor: colors.mossSoft, alignItems: 'center', justifyContent: 'center' },
  quoteMarkText: { color: colors.moss, fontSize: 22, lineHeight: 27, fontWeight: '800' },
  citationCopy: { flex: 1 },
  citationHandle: { color: colors.ink, fontSize: 13, fontWeight: '800', fontFamily: 'monospace' },
  citationScope: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 3 },
  citationFreshness: { color: colors.moss, fontSize: 10, fontWeight: '900', textTransform: 'uppercase' },
  detailGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  detailMain: { flexGrow: 1, flexBasis: 580, minWidth: 300 },
  detailSide: { flexGrow: 1, flexBasis: 300, minWidth: 260 },
  detailFull: { flexBasis: '100%', minWidth: 0 },
  timelineCard: { paddingHorizontal: 20, paddingVertical: 14 },
  timelineRow: { minHeight: 88, flexDirection: 'row', gap: 14 },
  timelineRail: { width: 18, alignItems: 'center', paddingTop: 6 },
  timelineDot: { width: 9, height: 9, borderRadius: 5, backgroundColor: colors.line },
  timelineDotActive: { backgroundColor: colors.moss },
  timelineLine: { width: 1, flex: 1, backgroundColor: colors.line, marginTop: 5 },
  timelineCopy: { flex: 1, paddingBottom: 20 },
  timelineTitleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: 10 },
  timelineTitle: { color: colors.ink, fontSize: 13, fontWeight: '800' },
  timelineTime: { color: colors.muted, fontSize: 10, fontWeight: '700' },
  timelineDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 6 },
  policyCard: { minHeight: 278, padding: 20 },
  policyTitle: { color: colors.ink, fontSize: 18, fontWeight: '800', marginTop: 18 },
  policyList: { gap: 12, marginTop: 20 },
  policyRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  policyCheck: { color: colors.moss, fontSize: 13, fontWeight: '900' },
  policyText: { color: colors.muted, fontSize: 12, lineHeight: 17, flex: 1 },
  emptyTitle: { color: colors.ink, fontSize: 16, fontWeight: '800', marginTop: 16 },
  emptyBody: { color: colors.muted, fontSize: 13, lineHeight: 18, marginTop: 8 },
  openSystem: { marginTop: 16, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  openSystemText: { color: colors.ink, fontWeight: '800', fontSize: 13 },
  openSystemArrow: { color: colors.muted, fontSize: 18, fontWeight: '800' },
  systemAction: { minHeight: 112, marginTop: 24, marginBottom: 12, borderRadius: radius.md, backgroundColor: colors.ink, padding: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 18 },
  systemActionEyebrow: { color: '#AFC19F', fontSize: 10, fontWeight: '900', letterSpacing: 1.1 },
  systemActionTitle: { color: '#FFF', fontSize: 18, fontWeight: '800', marginTop: 5 },
  systemActionBody: { color: '#C8CCC3', fontSize: 12, lineHeight: 17, marginTop: 4, maxWidth: 620 },
  systemActionArrow: { color: '#FFF', fontSize: 28, fontWeight: '400' },
  disabled: { opacity: 0.45 },
  pressed: { opacity: 0.72 },
});
