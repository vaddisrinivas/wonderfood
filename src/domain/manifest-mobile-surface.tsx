import { Link } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Page } from '@/src/components/ui';
import { resolveChatServerConfig } from '@/src/chat/client';
import {
  loadCatalog,
  setActiveDomainOverride,
  type MobileSurfaceAction,
  type MobileSurfaceCard,
  type MobileSurfaceQuery,
  type MobileSurfaceTone,
  type MobileSurfaceView,
} from '@/src/domain/catalog';
import { queryDomainCollections } from '@/src/domain/queries';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSDatabase } from '@/src/db/provider';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { radius, type LifeOSColors, useLifeOSTheme } from '@/src/theme';

function matchingRecords(query: MobileSurfaceQuery, records: DomainRecordViewModel[]) {
  const collections = new Set(query.collections);
  const matcher = query.match ? new RegExp(query.match, 'i') : null;
  return records
    .filter((record) => collections.has(record.collection))
    .filter((record) => !matcher || matcher.test([record.title, record.status, record.meta, record.body].join(' ')))
    .slice(0, query.limit);
}

function toneSurface(tone: MobileSurfaceTone, colors: LifeOSColors) {
  if (tone === 'moss') return colors.mossSoft;
  if (tone === 'amber') return colors.amberSoft;
  if (tone === 'plum') return colors.plumSoft;
  if (tone === 'blue') return colors.blueSoft;
  return colors.paper;
}

function toneInk(tone: MobileSurfaceTone, colors: LifeOSColors) {
  if (tone === 'moss') return colors.moss;
  if (tone === 'amber') return colors.amber;
  if (tone === 'plum') return colors.plum;
  if (tone === 'blue') return colors.blue;
  return colors.ink;
}

function coerceCollectionQuery(raw: unknown, fallback: string[]): { collections: string[]; match?: string; limit: number } {
  if (!raw || typeof raw !== 'object') {
    return { collections: fallback, match: undefined, limit: 6 };
  }
  const value = raw as Record<string, unknown>;
  const rawCollections = Array.isArray(value.collections) ? value.collections.filter((item): item is string => typeof item === 'string') : [];
  const rawLimit = Number(value.limit);
  const validLimit = Number.isInteger(rawLimit) && rawLimit >= 1 ? Math.min(20, rawLimit) : 6;
  const rawMatch = typeof value.match === 'string' && value.match.trim().length > 0 ? value.match : undefined;

  if (rawCollections.length > 0) {
    return { collections: rawCollections, match: rawMatch, limit: validLimit };
  }
  return { collections: fallback, match: rawMatch, limit: validLimit };
}

function isAllowedUrl(targetUrl: string, allowlist: string[] | undefined): boolean {
  if (!allowlist || allowlist.length === 0) {
    return false;
  }
  let parsed: URL;
  try {
    parsed = new URL(targetUrl);
  } catch {
    return false;
  }
  return allowlist.some((entry) => {
    const allow = String(entry || '').trim().toLowerCase();
    if (!allow) return false;
    if (allow === '*') return true;
    if (allow === parsed.origin) return true;
    if (allow === parsed.hostname) return true;
    if (allow.startsWith('*.') && parsed.hostname.endsWith(allow.slice(2))) return true;
    return parsed.href.startsWith(allow);
  });
}

function isObjectValue(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object';
}

function toUiString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function toUiPayload(value: unknown): Record<string, unknown> | null {
  if (!isObjectValue(value)) {
    return null;
  }
  try {
    return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function makeUiRecordsQuery(
  manifestCollections: string[],
  component: Record<string, unknown> | undefined,
) {
  const { collections, match, limit } = coerceCollectionQuery(component?.query, manifestCollections);
  return { collections, match, limit };
}

function SurfaceAction({ action, filled = false }: { action?: MobileSurfaceAction; filled?: boolean }) {
  const theme = useLifeOSTheme();
  if (!action) return null;
  return (
    <Link href={action.href as never} asChild>
      <Pressable
        accessibilityRole="button"
        style={StyleSheet.flatten([
          styles.action,
          {
            backgroundColor: filled ? theme.colors.ink : 'transparent',
            borderColor: filled ? theme.colors.ink : theme.colors.line,
          },
        ])}>
        <Text maxFontSizeMultiplier={1.25} style={[styles.actionText, { color: filled ? theme.colors.paper : theme.colors.ink }]}>{action.label}</Text>
      </Pressable>
    </Link>
  );
}

function UiActionButton({
  actionLabel,
  onPress,
  disabled = false,
  filled = false,
}: { actionLabel?: string; onPress: () => void; disabled?: boolean; filled?: boolean }) {
  const theme = useLifeOSTheme();
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      style={StyleSheet.flatten([
        styles.action,
        {
          backgroundColor: disabled ? theme.colors.line : filled ? theme.colors.ink : 'transparent',
          borderColor: disabled ? theme.colors.line : (filled ? theme.colors.ink : theme.colors.line),
          opacity: disabled ? 0.8 : 1,
        },
      ])}
      onPress={onPress}>
      <Text
        maxFontSizeMultiplier={1.2}
        style={[styles.actionText, { color: disabled ? theme.colors.muted : (filled ? theme.colors.paper : theme.colors.ink) }]}>
        {actionLabel || 'Open'}
      </Text>
    </Pressable>
  );
}

function TextSection({ title, subtitle }: { title: string; subtitle?: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.textSection}>
      <Text maxFontSizeMultiplier={1.3} style={[styles.textTitle, { color: theme.colors.ink }]}>{title}</Text>
      {subtitle ? <Text maxFontSizeMultiplier={1.2} style={[styles.textSubtitle, { color: theme.colors.muted }]}>{subtitle}</Text> : null}
    </View>
  );
}

function SectionHeading({ card }: { card: MobileSurfaceCard }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.sectionHeading}>
      <View style={[styles.sectionMark, { backgroundColor: toneSurface(card.tone, theme.colors) }]}>
        <Text allowFontScaling={false} style={[styles.sectionMarkText, { color: toneInk(card.tone, theme.colors) }]}>{card.icon}</Text>
      </View>
      <Text maxFontSizeMultiplier={1.25} style={[styles.sectionLabel, { color: theme.colors.ink }]}>{card.label}</Text>
    </View>
  );
}

function EmptyContent({ card }: { card: MobileSurfaceCard }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.emptyContent}>
      <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>{card.empty.title}</Text>
      <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>{card.empty.detail}</Text>
    </View>
  );
}

function RecordRow({ card, record }: { card: MobileSurfaceCard; record: DomainRecordViewModel }) {
  const theme = useLifeOSTheme();
  return (
    <Link href={`/record/${encodeURIComponent(record.id)}` as never} asChild>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Open ${record.title}`}
        style={StyleSheet.flatten([styles.recordRow, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }])}>
        <View style={[styles.recordMark, { backgroundColor: toneSurface(card.tone, theme.colors) }]} />
        <View style={styles.recordCopy}>
          <Text maxFontSizeMultiplier={1.25} style={[styles.recordTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
          <Text maxFontSizeMultiplier={1.25} style={[styles.recordDetail, { color: theme.colors.muted }]} numberOfLines={2}>{record.meta || record.body}</Text>
        </View>
        {record.status ? <StatusPill tone={card.tone} status={record.status} /> : null}
      </Pressable>
    </Link>
  );
}

function ListSection({ card, records }: { card: MobileSurfaceCard; records: DomainRecordViewModel[] }) {
  const matches = matchingRecords(card.query, records);
  return (
    <View style={styles.section}>
      <SectionHeading card={card} />
      <View style={styles.recordList}>
        {matches.length ? matches.map((record) => <RecordRow key={record.id} card={card} record={record} />) : <EmptyContent card={card} />}
      </View>
      <SurfaceAction action={card.action} />
    </View>
  );
}

function FaceLine({ label, record, empty }: { label: string; record?: DomainRecordViewModel; empty: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.faceLine, { borderTopColor: theme.colors.line }]}>
      <Text maxFontSizeMultiplier={1.25} style={[styles.faceLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text maxFontSizeMultiplier={1.25} style={[styles.faceValue, { color: theme.colors.ink }]} numberOfLines={1}>{record?.title ?? empty}</Text>
    </View>
  );
}

function StatusPill({ tone, status }: { tone: MobileSurfaceTone; status: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.statusPill, { backgroundColor: toneSurface(tone, theme.colors) }]}>
      <Text maxFontSizeMultiplier={1.15} style={[styles.statusText, { color: theme.colors.ink }]} numberOfLines={1}>{status}</Text>
    </View>
  );
}

function FocusSection({ card, records }: { card: MobileSurfaceCard; records: DomainRecordViewModel[] }) {
  const theme = useLifeOSTheme();
  const primary = matchingRecords(card.query, records)[0];
  return (
    <View style={[
      styles.focusSection,
      {
        backgroundColor: toneSurface(card.tone, theme.colors),
        borderColor: card.tone === 'neutral' ? theme.colors.line : toneSurface(card.tone, theme.colors),
      },
    ]}>
      <SectionHeading card={card} />
      {primary ? (
        <Link href={`/record/${encodeURIComponent(primary.id)}` as never} asChild>
          <Pressable accessibilityRole="button" accessibilityLabel={`Open ${primary.title}`} style={styles.focusBody}>
            <View style={styles.focusTitleRow}>
              <Text maxFontSizeMultiplier={1.25} style={[styles.focusTitle, { color: theme.colors.ink }]} numberOfLines={2}>{primary.title}</Text>
              {primary.status ? <StatusPill tone={card.tone} status={primary.status} /> : null}
            </View>
            <Text maxFontSizeMultiplier={1.25} style={[styles.focusDetail, { color: theme.colors.muted }]} numberOfLines={3}>{primary.meta || primary.body}</Text>
          </Pressable>
        </Link>
      ) : <EmptyContent card={card} />}
      {card.nested.map((nested) => (
        <FaceLine
          key={nested.id}
          label={nested.label}
          record={matchingRecords(nested.query, records)[0]}
          empty={nested.empty}
        />
      ))}
      <SurfaceAction action={card.action} filled={card.kind === 'review'} />
    </View>
  );
}

function uiTone(value: unknown): MobileSurfaceTone {
  if (value === 'moss' || value === 'amber' || value === 'plum' || value === 'blue' || value === 'neutral') {
    return value;
  }
  return 'neutral';
}

function countSummary(records: DomainRecordViewModel[]) {
  return `${records.length} ${records.length === 1 ? 'record' : 'records'}`;
}

function metricRows(queryRecords: DomainRecordViewModel[]) {
  const title = queryRecords.length === 1 ? 'record' : 'records';
  return `${queryRecords.length} ${title}`;
}

function MetricSection({
  component,
  records,
}: { component: { title?: unknown; subtitle?: unknown; tone?: unknown; id?: unknown }, records: DomainRecordViewModel[] }) {
  const tone = uiTone(component.tone);
  const theme = useLifeOSTheme();
  const title = typeof component.title === 'string' && component.title.trim() ? component.title : 'Metric';

  return (
    <View style={[styles.metricSection, { borderColor: toneSurface(tone, theme.colors) }]}>
      <Text maxFontSizeMultiplier={1.25} style={[styles.metricTitle, { color: theme.colors.ink }]}>{title}</Text>
      <Text maxFontSizeMultiplier={1.35} style={[styles.metricValue, { color: toneInk(tone, theme.colors) }]}>{metricRows(records)}</Text>
      {typeof component.subtitle === 'string' && component.subtitle.trim() ? (
        <Text maxFontSizeMultiplier={1.2} style={[styles.textSubtitle, { color: theme.colors.muted }]}>{component.subtitle}</Text>
      ) : null}
    </View>
  );
}

function RecordListSection({
  component,
  manifestCollections,
  records,
  onAction,
  actionLabel,
  showHeader = true,
}: {
  component: { title?: unknown; subtitle?: unknown; tone?: unknown; action?: unknown; query?: unknown; id?: unknown };
  manifestCollections: string[];
  records: DomainRecordViewModel[];
  onAction?: () => void;
  actionLabel?: string;
  showHeader?: boolean;
}) {
  const tone = uiTone(component.tone);
  const theme = useLifeOSTheme();
  const title = typeof component.title === 'string' && component.title.trim() ? component.title : 'Items';
  const subtitle = typeof component.subtitle === 'string' && component.subtitle.trim() ? component.subtitle : 'No items';
  const action = component.action as unknown;
  const cardTone = toneSurface(tone, theme.colors);
  const card: MobileSurfaceCard = {
    kind: 'list',
    id: String(component.id || title).slice(0, 40),
    label: title,
    icon: tone === 'neutral' ? '◻' : '◆',
    tone,
    query: (() => {
      const query = makeUiRecordsQuery(manifestCollections, component as Record<string, unknown>);
      return { collections: query.collections, match: query.match, limit: query.limit };
    })(),
    empty: { title: subtitle, detail: `No ${title.toLowerCase()} found.` },
    action: component.action as MobileSurfaceCard['action'],
    nested: [],
  };

  const rows = matchingRecords(card.query, records);
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeading}>
        <View style={[styles.sectionMark, { backgroundColor: cardTone }]}>
          <Text allowFontScaling={false} style={[styles.sectionMarkText, { color: toneInk(tone, theme.colors) }]}>◦</Text>
        </View>
        {showHeader ? <Text maxFontSizeMultiplier={1.25} style={[styles.sectionLabel, { color: theme.colors.ink }]}>{title}</Text> : null}
      </View>
      <View style={styles.recordList}>
        {rows.length ? rows.map((record) => <RecordRow key={record.id} card={card} record={record} />) : <EmptyContent card={card} />}
      </View>
      {action && onAction ? (
        <UiActionButton actionLabel={actionLabel || 'Run'} onPress={onAction} />
      ) : null}
    </View>
  );
}

function LoadingSurface() {
  const theme = useLifeOSTheme();
  return (
    <View accessibilityLabel="Loading Food workspace" style={styles.loading}>
      {[0, 1, 2].map((index) => (
        <View key={index} style={[styles.loadingRow, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
          <View style={[styles.loadingMark, { backgroundColor: theme.colors.line }]} />
          <View style={styles.loadingCopy}>
            <View style={[styles.loadingLine, { backgroundColor: theme.colors.line, width: index === 1 ? '52%' : '68%' }]} />
            <View style={[styles.loadingLine, styles.loadingLineShort, { backgroundColor: theme.colors.line }]} />
          </View>
        </View>
      ))}
    </View>
  );
}

function fallbackView(title: string, subtitle: string, cardOrder: string[]): MobileSurfaceView {
  return { id: 'home', label: 'Home', icon: '⌂', title, subtitle, card_order: cardOrder };
}

/** Generic, package-owned mobile workspace. Domain copy, views, queries and actions live in the active package. */
export function ManifestMobileSurface() {
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const { activeManifest } = loadCatalog();
  const db = useLifeOSDatabase();
  const theme = useLifeOSTheme();
  const ui = activeManifest.ui;
  const uiScreens = useMemo(() => {
    if (!ui?.screens || Object.keys(ui.screens).length === 0) {
      return [];
    }
    return Object.entries(ui.screens).map(([screenId, screen]) => {
      const value = screen as Record<string, unknown>;
      return {
        id: screenId,
        title: typeof value.title === 'string' && value.title.trim() ? value.title : screenId,
        subtitle: typeof value.subtitle === 'string' && value.subtitle.trim() ? value.subtitle : 'Screen',
        components: Array.isArray(value.components) ? value.components : [],
      };
    });
  }, [ui?.screens]);
  const hasUi = Boolean(
    ui && (
      (Array.isArray(ui.components) && ui.components.length > 0)
      || (uiScreens.length > 0)
    )
  );
  const [selectedUiScreenId, setSelectedUiScreenId] = useState(uiScreens[0]?.id ?? '');
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);
  const surface = activeManifest.mobile_surface;
  const views = useMemo(
    () => surface?.views?.length ? surface.views : surface ? [fallbackView(surface.title, surface.subtitle, surface.card_order)] : [],
    [surface],
  );
  const initialView = surface?.default_view ?? views[0]?.id ?? '';
  const [selectedViewId, setSelectedViewId] = useState(initialView);
  const [records, setRecords] = useState<DomainRecordViewModel[]>([]);
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!views.some((view) => view.id === selectedViewId)) setSelectedViewId(surface?.default_view ?? views[0]?.id ?? '');
  }, [selectedViewId, surface?.default_view, views]);

  useEffect(() => {
    let current = true;
    setLoadState('loading');
    queryDomainCollections(db, activeManifest.collections)
      .then((next) => {
        if (!current) return;
        setRecords(next);
        setLoadState('ready');
      })
      .catch(() => {
        if (!current) return;
        setRecords([]);
        setLoadState('error');
      });
    return () => { current = false; };
  }, [activeManifest.collections, activeManifest.id, db, reloadToken]);

  useEffect(() => {
    if (uiScreens.length && !selectedUiScreenId) {
      setSelectedUiScreenId(uiScreens[0]?.id ?? '');
    }
  }, [selectedUiScreenId, uiScreens]);

  if (hasUi) {
    const fallbackComponents = Array.isArray(ui?.components) ? ui.components : [];
    const hasUiScreens = uiScreens.length > 0;
    const selectedScreen = uiScreens.find((screen) => screen.id === selectedUiScreenId) ?? uiScreens[0];
    const screenComponents = hasUiScreens ? (selectedScreen?.components ?? []) : fallbackComponents;
    const safeScreenComponents = Array.isArray(screenComponents) ? screenComponents : [];

    async function handleUiAction(rawAction: unknown) {
      if (!rawAction || typeof rawAction !== 'object') {
        setActionFeedback('No action attached to this component.');
        return;
      }
      const action = rawAction as Record<string, unknown>;
      const kind = toUiString(action.kind);
      if (kind !== 'open_url' && kind !== 'propose') {
        setActionFeedback('Unknown component action.');
        return;
      }

      if (kind === 'open_url') {
        const url = toUiString(action.url);
        if (!url || !isAllowedUrl(url, ui?.openUrlAllowlist)) {
          setActionFeedback('This link is not allowed for this package.');
          return;
        }
        try {
          await Linking.openURL(url);
          setActionFeedback('Opened link.');
        } catch {
          setActionFeedback('Could not open link.');
        }
        return;
      }

      const command = toUiString(action.command);
      const tool = toUiString(action.tool);
      const normalizedCommand = command || tool;
      if (!normalizedCommand) {
        setActionFeedback('Missing proposal command.');
        return;
      }
      const payload = toUiPayload(action.payload);
      const serverConfig = await resolveChatServerConfig();
      if (!serverConfig.serverUrl) {
        setActionFeedback('Server unavailable for proposals.');
        return;
      }
      try {
        const endpoint = serverConfig.serverUrl.replace(/\/$/, '');
        const response = await fetch(`${endpoint}/chat/action`, {
          method: 'POST',
          headers: {
            'content-type': 'application/json',
            ...(serverConfig.serverToken ? { authorization: `Bearer ${serverConfig.serverToken}` } : {}),
          },
          body: JSON.stringify({
            action: 'propose',
            command: normalizedCommand,
            tool: tool || undefined,
            payload: payload ?? undefined,
            domain_id: activeManifest.id,
            actor: 'ui-package',
            idempotency_key: `ui:${Date.now()}:${Math.floor(Math.random() * 1000)}`,
          }),
        });
        if (!response.ok) {
          setActionFeedback('Proposal request failed.');
          return;
        }
        setActionFeedback('Proposal queued.');
      } catch {
        setActionFeedback('Could not queue proposal.');
      }
    }

    function renderUiComponent(component: unknown, index: number) {
      if (!component || typeof component !== 'object') {
        return (
          <View style={styles.section} key={`ui-unsupported-${index}`}>
            <TextSection title="Unsupported component" subtitle="Missing component payload." />
          </View>
        );
      }
      const typed = component as Record<string, unknown>;
      const kind = typed.kind;
      const title = typeof typed.title === 'string' && typed.title.trim() ? typed.title : undefined;
      if (kind === 'recordList') {
        const query = makeUiRecordsQuery(activeManifest.collections, typed as { query?: unknown } as Record<string, unknown>);
        const rows = matchingRecords({ collections: query.collections, match: query.match, limit: query.limit }, records);
        const queryMeta = { title: title || 'Items', subtitle: typed.subtitle, tone: typed.tone, action: typed.action, query: typed.query };
        return (
          <RecordListSection
            key={`ui-${typed.id ?? index}-${kind}`}
            manifestCollections={activeManifest.collections}
            component={queryMeta}
            records={rows}
            onAction={() => void handleUiAction(typed.action)}
            actionLabel={typeof typed.action === 'object' && typeof (typed.action as Record<string, unknown>).label === 'string'
              ? String((typed.action as Record<string, unknown>).label)
              : 'Action'}
          />
        );
      }
      if (kind === 'metric') {
        const query = makeUiRecordsQuery(activeManifest.collections, typed as { query?: unknown } as Record<string, unknown>);
        const rows = matchingRecords({ collections: query.collections, match: query.match, limit: query.limit }, records);
        return <MetricSection key={`ui-${typed.id ?? index}-${kind}`} component={{ title, subtitle: typed.subtitle, tone: typed.tone, id: typed.id }} records={rows} />;
      }
      if (kind === 'text') {
        return <TextSection key={`ui-${typed.id ?? index}-${kind}`} title={title || 'Note'} subtitle={typeof typed.subtitle === 'string' ? typed.subtitle : ''} />;
      }
      if (kind === 'action') {
        return (
          <View key={`ui-${typed.id ?? index}-${kind}`} style={styles.section}>
            <UiActionButton
              actionLabel={title || 'Run'}
              onPress={() => void handleUiAction(typed.action)}
            />
          </View>
        );
      }
      return (
        <View style={styles.section} key={`ui-unsupported-${index}`}>
          <TextSection title="Unsupported component" subtitle={`Unknown kind: ${String(kind || 'unknown')}`} />
        </View>
      );
    }

    const activeScreenTitle = selectedScreen?.title || activeManifest.label;
    const activeScreenSubtitle = selectedScreen?.subtitle ?? '';

    return (
      <Page>
        <ScrollView contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
          <View style={styles.content}>
            <View style={styles.header}>
              <View style={styles.headerCopy}>
                <Text maxFontSizeMultiplier={1.2} style={[styles.title, { color: theme.colors.ink }]}>{activeScreenTitle}</Text>
                {activeScreenSubtitle ? <Text maxFontSizeMultiplier={1.25} style={[styles.subtitle, { color: theme.colors.muted }]}>{activeScreenSubtitle}</Text> : null}
              </View>
            </View>

            {hasUiScreens && uiScreens.length > 1 ? (
              <ScrollView
                horizontal
                accessibilityRole="tablist"
                contentContainerStyle={styles.viewTabs}
                showsHorizontalScrollIndicator={false}>
                {uiScreens.map((screen) => {
                  const selected = screen.id === selectedScreen?.id;
                  return (
                    <Pressable
                      key={screen.id}
                      accessibilityRole="tab"
                      accessibilityState={{ selected }}
                      onPress={() => setSelectedUiScreenId(screen.id)}
                      style={({ pressed }) => [
                        styles.viewTab,
                        {
                          backgroundColor: selected ? theme.colors.ink : theme.colors.paper,
                          borderColor: selected ? theme.colors.ink : theme.colors.line,
                        },
                        pressed && styles.pressed,
                      ]}>
                      <Text maxFontSizeMultiplier={1.2} style={[styles.viewTabLabel, { color: selected ? theme.colors.paper : theme.colors.ink }]}>{screen.title}</Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
            ) : null}

            {loadState === 'loading' ? <LoadingSurface /> : null}
            {loadState === 'error' ? (
              <View style={[styles.errorState, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
                <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>Food could not load.</Text>
                <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>Your data is untouched. Try the local view again.</Text>
                <Pressable accessibilityRole="button" onPress={() => setReloadToken((value) => value + 1)} style={[styles.action, { borderColor: theme.colors.line }]}>
                  <Text maxFontSizeMultiplier={1.25} style={[styles.actionText, { color: theme.colors.ink }]}>Try again</Text>
                </Pressable>
              </View>
            ) : null}
            {loadState === 'ready' ? (
              <View style={styles.sections}>
                {safeScreenComponents.length ? (
                  safeScreenComponents.map((component, index) => renderUiComponent(component, index))
                ) : (
                  <View style={styles.centered}>
                    <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>UI shell is empty.</Text>
                    <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>Ask Wonder to add components to this screen.</Text>
                  </View>
                )}
              </View>
            ) : null}
            {actionFeedback ? <Text maxFontSizeMultiplier={1.25} style={[styles.actionFeedback, { color: theme.colors.muted }]}>{actionFeedback}</Text> : null}
          </View>
        </ScrollView>
      </Page>
    );
  }

  if (!surface) {
    return (
      <Page>
        <View style={styles.centered}>
          <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>This workspace needs a mobile surface.</Text>
          <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>Ask Wonder to create one from the active package.</Text>
        </View>
      </Page>
    );
  }

  const activeView = views.find((view) => view.id === selectedViewId) ?? views[0];
  const cardsById = new Map(surface.cards.map((card) => [card.id, card]));
  const visibleCards = (activeView?.card_order ?? surface.card_order)
    .map((id) => cardsById.get(id))
    .filter((card): card is MobileSurfaceCard => Boolean(card));
  const readyCards = visibleCards.filter((card) => card.kind !== 'review' || matchingRecords(card.query, records).length > 0);

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <View style={styles.header}>
            <View style={styles.headerCopy}>
              <Text maxFontSizeMultiplier={1.2} style={[styles.title, { color: theme.colors.ink }]}>{activeView?.title ?? surface.title}</Text>
              <Text maxFontSizeMultiplier={1.25} style={[styles.subtitle, { color: theme.colors.muted }]}>{activeView?.subtitle ?? surface.subtitle}</Text>
            </View>
            <SurfaceAction action={surface.header_action} filled />
          </View>

          {views.length > 1 ? (
            <ScrollView
              horizontal
              accessibilityRole="tablist"
              contentContainerStyle={styles.viewTabs}
              showsHorizontalScrollIndicator={false}>
              {views.map((view) => {
                const selected = view.id === activeView?.id;
                return (
                  <Pressable
                    key={view.id}
                    accessibilityRole="tab"
                    accessibilityState={{ selected }}
                    onPress={() => setSelectedViewId(view.id)}
                    style={({ pressed }) => [
                      styles.viewTab,
                      {
                        backgroundColor: selected ? theme.colors.ink : theme.colors.paper,
                        borderColor: selected ? theme.colors.ink : theme.colors.line,
                      },
                      pressed && styles.pressed,
                    ]}>
                    <Text allowFontScaling={false} style={styles.viewTabIcon}>{view.icon}</Text>
                    <Text maxFontSizeMultiplier={1.2} style={[styles.viewTabLabel, { color: selected ? theme.colors.paper : theme.colors.ink }]}>{view.label}</Text>
                  </Pressable>
                );
              })}
            </ScrollView>
          ) : null}

          {loadState === 'loading' ? <LoadingSurface /> : null}
          {loadState === 'error' ? (
            <View style={[styles.errorState, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
              <Text maxFontSizeMultiplier={1.25} style={[styles.emptyTitle, { color: theme.colors.ink }]}>Food could not load.</Text>
              <Text maxFontSizeMultiplier={1.25} style={[styles.emptyDetail, { color: theme.colors.muted }]}>Your data is untouched. Try the local view again.</Text>
              <Pressable accessibilityRole="button" onPress={() => setReloadToken((value) => value + 1)} style={[styles.action, { borderColor: theme.colors.line }]}>
                <Text maxFontSizeMultiplier={1.25} style={[styles.actionText, { color: theme.colors.ink }]}>Try again</Text>
              </Pressable>
            </View>
          ) : null}
          {loadState === 'ready' ? (
            <View style={styles.sections}>
              {readyCards.map((card) => card.kind === 'list'
                ? <ListSection key={card.id} card={card} records={records} />
                : <FocusSection key={card.id} card={card} records={records} />)}
            </View>
          ) : null}

          {surface.advanced.visible ? (
            <View style={styles.advanced}>
              {surface.advanced.cards.map((card) => card.kind === 'list'
                ? <ListSection key={card.id} card={card} records={records} />
                : <FocusSection key={card.id} card={card} records={records} />)}
            </View>
          ) : null}
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  content: { width: '100%', maxWidth: 720, alignSelf: 'center', paddingHorizontal: 16, paddingTop: 8, paddingBottom: 28 },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, marginBottom: 12 },
  headerCopy: { flex: 1, minWidth: 0 },
  title: { fontSize: 28, lineHeight: 32, fontWeight: '800', letterSpacing: -0.7 },
  subtitle: { maxWidth: 500, fontSize: 12, lineHeight: 16, marginTop: 2 },
  viewTabs: { gap: 7, paddingBottom: 16 },
  viewTab: { minHeight: 42, borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  viewTabIcon: { fontSize: 14 },
  viewTabLabel: { fontSize: 12, fontWeight: '800' },
  sections: { gap: 18 },
  textSection: { gap: 4 },
  textTitle: { fontSize: 18, lineHeight: 22, fontWeight: '800', letterSpacing: -0.2 },
  textSubtitle: { fontSize: 12, lineHeight: 16, marginTop: 3 },
  metricSection: { borderWidth: 1, borderRadius: radius.md, padding: 12, gap: 6 },
  metricTitle: { fontSize: 12, lineHeight: 16, fontWeight: '800' },
  metricValue: { fontSize: 28, lineHeight: 34, fontWeight: '900' },
  section: { gap: 7 },
  sectionHeading: { minHeight: 26, flexDirection: 'row', alignItems: 'center', gap: 8 },
  sectionMark: { width: 25, height: 25, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  sectionMarkText: { fontSize: 14, fontWeight: '900' },
  sectionLabel: { flex: 1, fontSize: 16, lineHeight: 20, fontWeight: '800', letterSpacing: -0.2 },
  recordList: { gap: 0 },
  recordRow: { minHeight: 60, borderWidth: 0, borderBottomWidth: StyleSheet.hairlineWidth, borderRadius: 0, paddingVertical: 8, paddingHorizontal: 2, flexDirection: 'row', alignItems: 'center', gap: 10 },
  recordMark: { width: 5, height: 34, borderRadius: 3 },
  recordCopy: { flex: 1, minWidth: 0 },
  recordTitle: { fontSize: 14, lineHeight: 18, fontWeight: '800' },
  recordDetail: { fontSize: 12, lineHeight: 16, marginTop: 2 },
  statusPill: { maxWidth: 118, minHeight: 28, borderRadius: radius.pill, paddingHorizontal: 9, alignItems: 'center', justifyContent: 'center' },
  statusText: { fontSize: 11, lineHeight: 14, fontWeight: '800' },
  focusSection: { borderWidth: 1, borderRadius: radius.md, padding: 13, gap: 8 },
  focusBody: { minHeight: 42, justifyContent: 'center' },
  focusTitleRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 },
  focusTitle: { flex: 1, fontSize: 18, lineHeight: 22, fontWeight: '800', letterSpacing: -0.25 },
  focusDetail: { fontSize: 13, lineHeight: 18, marginTop: 4 },
  faceLine: { minHeight: 42, borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 9, flexDirection: 'row', alignItems: 'center', gap: 8 },
  faceLabel: { fontSize: 11, lineHeight: 15, fontWeight: '800' },
  faceValue: { flex: 1, fontSize: 12, lineHeight: 16, fontWeight: '700' },
  emptyContent: { minHeight: 60, justifyContent: 'center' },
  emptyTitle: { fontSize: 15, lineHeight: 20, fontWeight: '800' },
  emptyDetail: { fontSize: 12, lineHeight: 17, marginTop: 3 },
  action: { minHeight: 40, alignSelf: 'flex-start', borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: 13, alignItems: 'center', justifyContent: 'center' },
  actionText: { fontSize: 12, lineHeight: 16, fontWeight: '800' },
  pressed: { opacity: 0.66 },
  loading: { gap: 8 },
  loadingRow: { minHeight: 72, borderWidth: 1, borderRadius: radius.sm, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 12 },
  loadingMark: { width: 18, height: 36, borderRadius: 7, opacity: 0.65 },
  loadingCopy: { flex: 1, gap: 7 },
  loadingLine: { height: 10, borderRadius: radius.pill, opacity: 0.65 },
  loadingLineShort: { width: '84%', height: 8, opacity: 0.4 },
  errorState: { borderWidth: 1, borderRadius: radius.md, padding: 16, gap: 8 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28 },
  actionFeedback: { marginTop: 8, fontSize: 12, lineHeight: 16 },
  advanced: { marginTop: 24, gap: 18 },
});
