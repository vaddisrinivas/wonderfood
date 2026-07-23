import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Card, Page, PageHeader, Pill, SectionTitle, VisualMark, sharedStyles } from '@/src/components/ui';
import { listSourceRows } from '@/src/domain/queries';
import { useLifeOSDatabase } from '@/src/db/provider';
import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { DirectSyncReceipt, clearProviderLocalCopy, restoreClearedProviderLocalCopy, syncConfiguredSources, syncNotionDirect, syncSheetsDirect } from '@/src/providers/direct-source-sync';
import { LifeOSSettings, defaultLifeOSSettings, loadLifeOSSettings, saveLifeOSSettings } from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';
import { mergeVisualIdentity, visualAccent, visualGlyph } from '@/src/domain/visual-identity';

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
    role: 'Notion home',
    summary: 'Pull selected Notion databases and pages into LifeOS, then ask Chat with exact citations.',
    scope: 'Manual pull',
    action: 'Configure provider',
    href: null,
  },
  'google_sheets': {
    icon: '▦',
    tone: 'blue' as Tone,
    role: 'Sheets home',
    summary: 'Use a workbook as your spreadsheet-first surface while LifeOS keeps rows source-backed.',
    scope: 'Manual pull',
    action: 'Configure provider',
    href: null,
  },
  sqlite: {
    icon: '▣',
    tone: 'plum' as Tone,
    role: 'Always available',
    summary: 'Your local LifeOS copy keeps records, recent changes, citations and chat context available offline.',
    scope: 'On device',
    action: 'Open local graph',
    href: null,
  },
  postgres: {
    icon: 'P',
    tone: 'amber' as Tone,
    role: 'Self-managed option',
    summary: 'Use Postgres when you want a durable household database beyond this device.',
    scope: 'Not connected',
    action: 'Configure database',
    href: null,
  },
} as const;

function normalizeSourceName(name: string): keyof typeof sourceMeta | 'other' {
  const normalized = name.toLowerCase().replace(/\s+/g, '_');
  if (normalized in sourceMeta) return normalized as keyof typeof sourceMeta;
  return 'other';
}

function citationSourcesFor(domainLabel: string, collections: string[]) {
  const collectionSummary = collections.slice(0, 5).join(', ') || `${domainLabel.toLowerCase()} records`;
  return [
    [`[${domainLabel} Notion]`, 'Pages, properties, relations and exact block quotes', 'When connected'],
    [`[${domainLabel} Sheets]`, 'Rows, cells, formulas and workbook timestamps', 'When connected'],
    [`[${domainLabel} app]`, `Local ${collectionSummary}, saved citations and recent actions`, 'On device'],
    [`[${domainLabel} rules]`, 'What the assistant is allowed to read, cite and change', 'Versioned'],
  ] as const;
}

const recentSync = [
  ['01', 'Choose homes', 'Use the app alone, or enable Notion, Sheets or Postgres from Connections.'],
  ['02', 'Pull into LifeOS', 'Bring selected pages or rows onto this device without a required server.'],
  ['03', 'Ask with sources', 'Chat answers from the local graph and opens the exact Notion, Sheets or device record it used.'],
  ['04', 'Review changes', 'Conflicts and provider-owned fields stay visible before anything important is overwritten.'],
] as const;

const defaultSourceSectionOrder = ['hero', 'metrics', 'dataHomes', 'citations', 'syncPlan', 'policy', 'configLink'] as const;
type SourceSectionId = typeof defaultSourceSectionOrder[number];

function orderedSourceSections(sectionOrder: string): SourceSectionId[] {
  const allowed = new Set<string>(defaultSourceSectionOrder);
  const requested = sectionOrder
    .split(',')
    .map((item) => item.trim())
    .filter((item): item is SourceSectionId => allowed.has(item));
  return [...requested, ...defaultSourceSectionOrder.filter((item) => !requested.includes(item))];
}

function toneColor(tone: Tone, palette = colors) {
  return {
    moss: palette.moss,
    amber: palette.amber,
    plum: palette.plum,
    blue: palette.blue,
  }[tone];
}

export default function SourcesScreen() {
  const theme = useLifeOSTheme();
  const { width } = useWindowDimensions();
  const compact = width < 720;
  const db = useLifeOSDatabase();
  const [sourceRows, setSourceRows] = useState<SourceRow[]>([]);
  const [settings, setSettings] = useState<LifeOSSettings>(defaultLifeOSSettings);
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeManifest } = loadCatalog();
  const visualIdentity = mergeVisualIdentity(activeManifest, settings.runtime.visualIdentityOverrides);
  const domainLabel = activeManifest.label;
  const sourcesConfig = settings.runtime.surfaceConfig.sources;
  const citationLimit = Math.max(1, Math.min(12, Number.parseInt(sourcesConfig.citationLimit, 10) || 4));
  const citationSources = citationSourcesFor(domainLabel, activeManifest.collections).slice(0, citationLimit);
  const [receipts, setReceipts] = useState<DirectSyncReceipt[]>([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState<DirectSyncReceipt['provider'] | 'all' | null>(null);
  const hasDb = Boolean(db);

  const refreshRows = async () => {
    setLoading(true);
    const loadedSettings = await loadLifeOSSettings();
    setActiveDomainOverride(loadedSettings.runtime.activeDomain);
    const rows = await listSourceRows(db);
    setSettings(loadedSettings);
    setSourceRows(mergeConfiguredRows(rows, loadedSettings));
    setLoading(false);
  };

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      const loadedSettings = await loadLifeOSSettings();
      setActiveDomainOverride(loadedSettings.runtime.activeDomain);
      const rows = await listSourceRows(db);
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

  const clearLocalProvider = async (provider: DirectSyncReceipt['provider']) => {
    setSyncing(provider);
    const result = await clearProviderLocalCopy({ db, provider });
    setReceipts([result]);
    await refreshRows();
    setSyncing(null);
  };

  const disconnectProvider = async (provider: DirectSyncReceipt['provider']) => {
    setSyncing(provider);
    const loadedSettings = await loadLifeOSSettings();
    const nextSettings = provider === 'notion'
      ? { ...loadedSettings, notion: { ...loadedSettings.notion, enabled: false } }
      : { ...loadedSettings, sheets: { ...loadedSettings.sheets, enabled: false } };
    const saved = await saveLifeOSSettings(nextSettings);
    const clearResult = await clearProviderLocalCopy({ db, provider });
    setSettings(saved);
    setReceipts([{
      ...clearResult,
      message: `Disconnected ${provider === 'notion' ? 'Notion' : 'Sheets'} in this app and cleared the local copy. Provider data was not changed.${clearResult.restoreToken ? ' You can restore the local copy for 15 minutes.' : ''}`,
    }]);
    await refreshRows();
    setSyncing(null);
  };

  const restoreLocalProvider = async (receipt: DirectSyncReceipt) => {
    setSyncing(receipt.provider);
    const result = await restoreClearedProviderLocalCopy({ db, restoreToken: receipt.restoreToken });
    setReceipts([result]);
    await refreshRows();
    setSyncing(null);
  };

  const configuredCount = [settings.notion.enabled, settings.sheets.enabled, settings.postgres.enabled, settings.mcp.enabled].filter(Boolean).length;
  const latestReceipt = receipts[0];
  const renderSourceSection = (section: SourceSectionId) => {
    if (section === 'hero') {
      return sourcesConfig.showHero ? (
        <>
          <PageHeader
            eyebrow="Connected sources"
            title={`Your ${domainLabel.toLowerCase()} data homes.`}
            subtitle={`Use LifeOS by itself, or connect Notion and Sheets when those are your preferred surfaces. Chat cites the exact page, row or device record it used.`}
          />

          <Card tone="blue" style={[styles.loopCard, compact ? styles.loopCardCompact : null]}>
            <View style={[styles.loopCopy, compact ? styles.loopCopyCompact : null]}>
              <Pill tone="blue">LOCAL FIRST</Pill>
              <Text style={[styles.loopTitle, { color: theme.colors.ink }]}>Pull what you want. Keep control.</Text>
              <Text style={[styles.loopBody, { color: theme.colors.muted }]}>
                Start with the app only. Add your own Notion databases or Sheets workbook later. Manual pulls keep the graph understandable and portable.
              </Text>
              <View style={styles.syncActions}>
                <Pressable accessibilityRole="button" disabled={Boolean(syncing)} onPress={() => void runSync('all')} style={({ pressed }) => [styles.primarySync, { backgroundColor: theme.colors.ink }, syncing && styles.disabled, pressed && styles.pressed]}>
                  <Text style={[styles.primarySyncText, { color: theme.colors.paper }]}>{syncing === 'all' ? 'Pulling...' : 'Pull enabled homes'}</Text>
                </Pressable>
                <Link href="/settings" asChild>
                  <Pressable accessibilityRole="button" style={({ pressed }) => [styles.secondarySync, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
                    <Text style={[styles.secondarySyncText, { color: theme.colors.ink }]}>Connections</Text>
                  </Pressable>
                </Link>
              </View>
            </View>
            <View style={[styles.loopFlow, compact ? styles.loopFlowCompact : null]} accessibilityLabel="Notion, Sheets or this device can become LifeOS records with citations">
              <View style={[styles.flowNode, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}><Text style={[styles.flowNodeLabel, { color: theme.colors.ink }]}>Notion / Sheets</Text><Text style={[styles.flowNodeRole, { color: theme.colors.muted }]}>Your homes</Text></View>
              <Text style={[styles.flowArrow, { color: theme.colors.blue }]}>→</Text>
              <View style={[styles.flowNode, styles.flowNodeCore, { backgroundColor: theme.colors.ink, borderColor: theme.colors.ink }]}><Text style={[styles.flowNodeCoreLabel, { color: theme.colors.paper }]}>{domainLabel}</Text><Text style={[styles.flowNodeCoreRole, { color: theme.colors.mossSoft }]}>Records</Text></View>
              <Text style={[styles.flowArrow, { color: theme.colors.blue }]}>→</Text>
              <View style={styles.flowDestinations}>
                <Text style={[styles.flowDestination, { color: theme.colors.blue, backgroundColor: theme.colors.paper }]}>On-device copy</Text>
                <Text style={[styles.flowDestination, { color: theme.colors.blue, backgroundColor: theme.colors.paper }]}>Citations</Text>
              </View>
            </View>
          </Card>
        </>
      ) : null;
    }
    if (section === 'metrics') {
      return sourcesConfig.showMetrics ? (
        <View style={styles.metrics}>
          <Card style={styles.metric}><Text style={[styles.metricValue, { color: theme.colors.ink }]}>{loading ? '...' : `${sourceRows.length}`}</Text><Text style={[styles.metricLabel, { color: theme.colors.ink }]}>data homes</Text><Text style={[styles.metricFoot, { color: theme.colors.muted }]}>Local plus connected providers</Text></Card>
          <Card style={styles.metric}><Text style={[styles.metricValue, { color: theme.colors.ink }]}>{configuredCount}</Text><Text style={[styles.metricLabel, { color: theme.colors.ink }]}>enabled settings</Text><Text style={[styles.metricFoot, { color: theme.colors.muted }]}>Editable in app</Text></Card>
          <Card style={styles.metric}><Text style={[styles.metricValue, { color: theme.colors.ink }]}>{latestReceipt ? `${latestReceipt.records}` : '0'}</Text><Text style={[styles.metricLabel, { color: theme.colors.ink }]}>last pull records</Text><Text style={[styles.metricFoot, { color: theme.colors.muted }]}>{latestReceipt?.message ?? 'No pull run this session'}</Text></Card>
        </View>
      ) : null;
    }
    if (section === 'dataHomes') {
      return sourcesConfig.showDataHomes ? (
        <>
          <SectionTitle title="Your data homes" />
          {!sourceRows.length ? (
            <Card>
              <Text style={styles.emptyTitle}>No external sources connected</Text>
              <Text style={styles.emptyBody}>
                {hasDb
                  ? 'Connect Notion or Sheets in Settings when you want source-backed pages and rows.'
                  : 'The app is ready. Add Notion or Sheets in Settings when you want.'}
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
                ? { icon: visualGlyph(visualIdentity.sources?.user, '◉'), tone: 'blue' as Tone, role: 'Unknown source', summary: `${sourceRow.name} source was found by the app.`, scope: sourceRow.freshness, action: 'Inspect source', href: null }
                : sourceMeta[normalized];
              const sourceVisual = normalized === 'other' ? undefined : visualIdentity.sources?.[normalized];
              const sourceTone = visualAccent(sourceVisual) as Tone;
              const isReady = normalized === 'postgres';
              const displayRole = normalized === 'sqlite' ? `${domainLabel} on this device` : meta.role;
              const displaySummary = meta.summary
                .replace('meals, kitchen, recipes and planning', `${domainLabel.toLowerCase()} pages, records and planning`)
                .replace('Fast offline graph', `Fast offline ${domainLabel.toLowerCase()} records`);
              return (
                <View key={sourceRow.name} style={[styles.sourceCell, compact ? styles.sourceCellCompact : null]}>
                  <Card style={styles.sourceCard}>
                    <View style={styles.sourceTop}>
                      <VisualMark token={sourceVisual} fallback={meta.icon} size={44} backgroundColor={toneColor(sourceTone, theme.colors)} color={theme.colors.paper} label={`${sourceRow.name} visual`} style={styles.sourceIcon} glyphStyle={styles.sourceIconText} />
                      <Pill tone={isReady ? 'amber' : meta.tone}>{sourceRow.status.toUpperCase()}</Pill>
                    </View>
                    <Text style={[styles.sourceName, { color: theme.colors.ink }]}>{sourceRow.name}</Text>
                    <Text style={[styles.sourceRole, { color: theme.colors.moss }]}>{displayRole} · {sourceRow.workspace}</Text>
                    <Text style={[styles.sourceSummary, { color: theme.colors.muted }]}>{displaySummary}</Text>
                    <View style={styles.sourceFacts}>
                      <View><Text style={[styles.factLabel, { color: theme.colors.muted }]}>FRESHNESS</Text><Text style={[styles.factValue, { color: theme.colors.ink }]}>{sourceRow.freshness}</Text></View>
                      <View><Text style={[styles.factLabel, { color: theme.colors.muted }]}>SCOPE</Text><Text style={[styles.factValue, { color: theme.colors.ink }]}>{meta.scope}</Text></View>
                    </View>
                    <View style={[styles.sourceActionsBlock, { borderTopColor: theme.colors.line }]}>
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
                        <Text style={[styles.sourceAction, { color: theme.colors.ink }]}>{normalized === 'notion' || normalized === 'google_sheets' ? 'Pull latest' : meta.action}</Text>
                        <Text style={[styles.sourceActionArrow, { color: theme.colors.muted }]}>{syncing === normalized ? '...' : meta.href ? '↗' : '→'}</Text>
                      </Pressable>
                      {normalized === 'notion' || normalized === 'google_sheets' ? (
                        <Pressable
                          accessibilityRole="button"
                          disabled={Boolean(syncing)}
                          onPress={() => void clearLocalProvider(normalized)}
                          style={styles.clearActionRow}
                        >
                          <Text style={[styles.clearAction, { color: theme.colors.muted }]}>Clear local copy</Text>
                          <Text style={[styles.sourceActionArrow, { color: theme.colors.muted }]}>⌫</Text>
                        </Pressable>
                      ) : null}
                      {normalized === 'notion' || normalized === 'google_sheets' ? (
                        <Pressable
                          accessibilityRole="button"
                          disabled={Boolean(syncing)}
                          onPress={() => void disconnectProvider(normalized)}
                          style={styles.clearActionRow}
                        >
                          <Text style={[styles.clearAction, { color: theme.colors.muted }]}>Disconnect in app</Text>
                          <Text style={[styles.sourceActionArrow, { color: theme.colors.muted }]}>⏻</Text>
                        </Pressable>
                      ) : null}
                    </View>
                  </Card>
                </View>
              );
            })}
          </View>
        </>
      ) : null;
    }
    if (section === 'citations') {
      return sourcesConfig.showCitations ? (
        <>
          <SectionTitle title={`What ${domainLabel} Chat can cite`} />
          <Card style={styles.citationCard}>
            <View style={styles.citationHeader}>
              <View>
                <Text style={styles.sectionLead}>Source-backed by default</Text>
                <Text style={styles.sectionDetail}>Every {domainLabel.toLowerCase()} claim opens the exact record, quote and observed version.</Text>
              </View>
              <Pill tone="moss">{citationSources.length} citeable homes</Pill>
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
        </>
      ) : null;
    }
    if (section === 'syncPlan') {
      return sourcesConfig.showSyncPlan ? (
        <View style={[styles.detailMain, compact ? styles.detailFull : null]}>
          <SectionTitle title="What happens when you pull" />
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
      ) : null;
    }
    if (section === 'policy') {
      return sourcesConfig.showPolicy ? (
        <View style={[styles.detailSide, compact ? styles.detailFull : null]}>
          <SectionTitle title="Your sync rules" />
          <Card tone="moss" style={styles.policyCard}>
            <Pill tone="moss">CALM BY DEFAULT</Pill>
            <Text style={styles.policyTitle}>No mystery writes.</Text>
            <View style={styles.policyList}>
              <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Your device stays available offline.</Text></View>
              <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Exact source versions stay attached to answers.</Text></View>
              <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Extra source fields are preserved.</Text></View>
              <View style={styles.policyRow}><Text style={styles.policyCheck}>✓</Text><Text style={styles.policyText}>Provider tokens are edited in Settings, never shown on this screen.</Text></View>
            </View>
          </Card>
        </View>
      ) : null;
    }
    return sourcesConfig.showConfigLink ? (
      <Link href="/config" asChild>
        <Pressable accessibilityRole="button" style={({ pressed }) => [styles.systemAction, pressed ? styles.pressed : null]}>
          <View>
            <Text style={styles.systemActionEyebrow}>CUSTOMIZE</Text>
            <Text style={styles.systemActionTitle}>Choose domains, skills and agents</Text>
            <Text style={styles.systemActionBody}>Tune how sources become LifeOS pages.</Text>
          </View>
          <Text style={styles.systemActionArrow}>→</Text>
        </Pressable>
      </Link>
    ) : null;
  };

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <View style={styles.contextBar}>
            <View>
              <Text style={[styles.brand, { color: theme.colors.blue }]}>LIFEOS / SOURCES</Text>
              <Text style={[styles.context, { color: theme.colors.muted }]}>{domainLabel} homes, freshness and citations</Text>
            </View>
            <View style={[styles.liveBadge, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}><View style={[styles.liveDot, { backgroundColor: theme.colors.moss }]} /><Text style={[styles.liveText, { color: theme.colors.ink }]}>{latestReceipt ? latestReceipt.status : 'Local ready'}</Text></View>
          </View>

          {orderedSourceSections(sourcesConfig.sectionOrder).map((section) => (
            <View key={section}>
              {renderSourceSection(section)}
            </View>
          ))}

          {receipts.length ? (
            <Card style={styles.receiptCard}>
              <Text style={styles.sectionLead}>Latest pull result</Text>
              <View style={styles.receiptList}>
                {receipts.map((receipt) => (
                  <View key={`${receipt.provider}-${receipt.observedAt}`} style={styles.receiptRow}>
                    <Pill tone={receipt.status === 'synced' ? 'moss' : receipt.status === 'blocked' || receipt.status === 'cleared' ? 'blue' : 'amber'}>{receipt.status.toUpperCase()}</Pill>
                    <View style={styles.receiptCopy}>
                      <Text style={styles.receiptTitle}>{receipt.provider.replace('_', ' ')}</Text>
                      <Text style={styles.receiptMessage}>{receipt.message}</Text>
                      {receipt.status === 'cleared' && receipt.restoreToken ? (
                        <Pressable accessibilityRole="button" disabled={Boolean(syncing)} onPress={() => void restoreLocalProvider(receipt)} style={({ pressed }) => [styles.restoreAction, pressed && styles.pressed]}>
                          <Text style={styles.restoreActionText}>{syncing === receipt.provider ? 'Restoring…' : 'Restore local copy'}</Text>
                        </Pressable>
                      ) : null}
                    </View>
                    <Text style={styles.receiptCount}>{receipt.status === 'cleared' ? `${receipt.records} cleared` : `${receipt.records} records`}</Text>
                  </View>
                ))}
              </View>
            </Card>
          ) : null}
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
  restoreAction: { alignSelf: 'flex-start', minHeight: 34, borderRadius: radius.pill, backgroundColor: colors.mossSoft, paddingHorizontal: 12, marginTop: 9, justifyContent: 'center' },
  restoreActionText: { color: colors.ink, fontSize: 12, fontWeight: '900' },
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
  sourceActionsBlock: { marginTop: 'auto', paddingTop: 14, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, gap: 8 },
  sourceActionRow: { minHeight: 36, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  clearActionRow: { minHeight: 34, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sourceAction: { color: colors.ink, fontSize: 12, fontWeight: '800' },
  clearAction: { color: colors.muted, fontSize: 11, fontWeight: '800' },
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
