import { compileSpecStream, validateSpec } from '@json-render/core';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { PackagePresentationUi, PackageUiAction, PackageUiComponent } from '@/packages/shared/contracts/package';
import { ActionButton, Card, Page, PageHeader, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { useLifeOSTheme } from '@/src/theme';

type JsonRenderSurfaceProps = {
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  ui?: PackagePresentationUi;
  screen?: string;
  records?: DomainRecordViewModel[];
  emptyTitle?: string;
};

type SurfaceScreen = NonNullable<PackagePresentationUi['screens']>[string];

const jsonRenderHealthSpec = compileSpecStream(
  '{"op":"add","path":"/root","value":"screen"}\n' +
  '{"op":"add","path":"/elements/screen","value":{"type":"View","props":{},"children":[]}}\n'
);

const jsonRenderCoreReady = validateSpec(jsonRenderHealthSpec as unknown as Parameters<typeof validateSpec>[0]).valid;

function normalize(text: unknown) {
  return String(text ?? '').toLowerCase();
}

function matchesRecord(record: DomainRecordViewModel, query: NonNullable<PackageUiComponent['query']>) {
  if (query.collections?.length && !query.collections.includes(record.collection)) {
    return false;
  }
  if (!query.match?.trim()) {
    return true;
  }
  try {
    const pattern = new RegExp(query.match, 'i');
    return pattern.test([record.title, record.body, record.meta, record.status, record.collection, record.source].join(' '));
  } catch {
    const needle = normalize(query.match);
    return [record.title, record.body, record.meta, record.status, record.collection, record.source]
      .some((value) => normalize(value).includes(needle));
  }
}

function queryRecords(records: DomainRecordViewModel[], query?: PackageUiComponent['query']) {
  if (!query) {
    return records.slice(0, 4);
  }
  return records.filter((record) => matchesRecord(record, query)).slice(0, query.limit ?? 4);
}

function toneFor(tone: PackageUiComponent['tone']) {
  return tone === 'moss' || tone === 'amber' || tone === 'plum' || tone === 'blue' ? tone : undefined;
}

function actionRoute(action?: PackageUiAction) {
  const route = action?.payload?.route;
  return typeof route === 'string' && route.startsWith('/') ? route : null;
}

function fallbackFor(component: PackageUiComponent) {
  if (component.kind === 'metric') return '0';
  if (component.query?.collections?.includes('shopping_item')) return 'No shopping blockers.';
  if (component.query?.collections?.includes('inventory')) return 'No urgent pantry items.';
  if (component.query?.collections?.includes('meal_plan')) return 'Ask Wonder to build tonight.';
  return 'Nothing here yet.';
}

function useJsonRenderScreen(ui?: PackagePresentationUi, screen?: string): SurfaceScreen | null {
  return useMemo(() => {
    if (!ui?.screens) {
      return ui?.components ? { components: ui.components } : null;
    }
    const screenId = screen ?? ui.defaultScreen ?? Object.keys(ui.screens)[0];
    return ui.screens[screenId] ?? ui.screens[ui.defaultScreen ?? ''] ?? Object.values(ui.screens)[0] ?? null;
  }, [screen, ui]);
}

function ComponentAction({ action }: { action?: PackageUiAction }) {
  const router = useRouter();
  if (!action?.label) {
    return null;
  }
  const route = actionRoute(action) ?? '/chat';
  return <ActionButton label={action.label} quiet onPress={() => router.push(route as never)} />;
}

function MetricBlock({ component, records }: { component: PackageUiComponent; records: DomainRecordViewModel[] }) {
  const theme = useLifeOSTheme();
  const rows = queryRecords(records, component.query);
  return (
    <Card tone={toneFor(component.tone)} style={styles.metricCard}>
      <Text style={[styles.metricValue, { color: theme.colors.ink }]}>{rows.length}</Text>
      <Text style={[styles.metricLabel, { color: theme.colors.ink }]}>{component.title ?? 'Metric'}</Text>
      {component.subtitle ? <Text style={[styles.copy, { color: theme.colors.muted }]}>{component.subtitle}</Text> : null}
    </Card>
  );
}

function TextBlock({ component }: { component: PackageUiComponent }) {
  const theme = useLifeOSTheme();
  return (
    <Card tone={toneFor(component.tone)}>
      <Text style={[styles.blockTitle, { color: theme.colors.ink }]}>{component.title ?? 'Section'}</Text>
      {component.subtitle ? <Text style={[styles.copy, { color: theme.colors.muted }]}>{component.subtitle}</Text> : null}
      <View style={styles.blockAction}>
        <ComponentAction action={component.action} />
      </View>
    </Card>
  );
}

function RecordListBlock({ component, records }: { component: PackageUiComponent; records: DomainRecordViewModel[] }) {
  const theme = useLifeOSTheme();
  const rows = queryRecords(records, component.query);
  return (
    <Card tone={toneFor(component.tone)}>
      <View style={styles.blockHeader}>
        <View style={styles.blockHeaderText}>
          <SectionTitle title={component.title ?? 'Records'} />
          {component.subtitle ? <Text style={[styles.copy, { color: theme.colors.muted }]}>{component.subtitle}</Text> : null}
        </View>
        <ComponentAction action={component.action} />
      </View>
      {rows.length ? rows.map((row) => (
        <Row
          key={row.id}
          icon={row.collection === 'meal_plan' ? '🍽️' : row.collection.includes('shopping') ? '🛒' : row.collection.includes('inventory') ? '🥬' : '✨'}
          title={row.title}
          detail={row.body || row.meta || row.status}
          href={{ pathname: '/record/[id]', params: { id: row.id } }}
        />
      )) : (
        <View style={styles.emptyState}>
          <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>{fallbackFor(component)}</Text>
          {component.action ? <ComponentAction action={component.action} /> : null}
        </View>
      )}
    </Card>
  );
}

function ActionBlock({ component }: { component: PackageUiComponent }) {
  const router = useRouter();
  const theme = useLifeOSTheme();
  const route = actionRoute(component.action) ?? '/chat';
  return (
    <Pressable accessibilityRole="button" onPress={() => router.push(route as never)} style={({ pressed }) => [pressed && styles.pressed]}>
      <Card tone={toneFor(component.tone)} style={styles.actionCard}>
        <View style={styles.actionCopy}>
          <Text style={[styles.blockTitle, { color: theme.colors.ink }]}>{component.title ?? component.action?.label ?? 'Open'}</Text>
          {component.subtitle ? <Text style={[styles.copy, { color: theme.colors.muted }]}>{component.subtitle}</Text> : null}
        </View>
        <Text style={[styles.chevron, { color: theme.colors.muted }]}>›</Text>
      </Card>
    </Pressable>
  );
}

function SurfaceComponent({ component, records }: { component: PackageUiComponent; records: DomainRecordViewModel[] }) {
  if (component.kind === 'recordList') return <RecordListBlock component={component} records={records} />;
  if (component.kind === 'metric') return <MetricBlock component={component} records={records} />;
  if (component.kind === 'action') return <ActionBlock component={component} />;
  return <TextBlock component={component} />;
}

export function JsonRenderSurface({ eyebrow, title, subtitle, ui, screen, records = [], emptyTitle = 'Nothing configured yet.' }: JsonRenderSurfaceProps) {
  const activeScreen = useJsonRenderScreen(ui, screen);
  const theme = useLifeOSTheme();
  const components = activeScreen?.components ?? [];
  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          {!jsonRenderCoreReady ? (
            <View style={styles.statusLine}>
              <Pill tone="amber">CONFIG FALLBACK</Pill>
            </View>
          ) : null}
          <PageHeader
            eyebrow={eyebrow}
            title={activeScreen?.title ?? title ?? 'Wonder'}
            subtitle={activeScreen?.subtitle ?? subtitle}
          />
          <View style={styles.stack}>
            {components.length ? components.map((component, index) => (
              <SurfaceComponent key={component.id ?? `${component.kind}-${index}`} component={component} records={records} />
            )) : (
              <Card>
                <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>{emptyTitle}</Text>
                <Text style={[styles.copy, { color: theme.colors.muted }]}>Ask Wonder to create or edit this surface.</Text>
              </Card>
            )}
          </View>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  statusLine: { alignItems: 'flex-start', paddingTop: 8, marginBottom: -4 },
  stack: { gap: 14, paddingBottom: 36 },
  blockHeader: { gap: 12 },
  blockHeaderText: { marginBottom: -8 },
  blockTitle: { fontSize: 18, lineHeight: 23, fontWeight: '800', letterSpacing: -0.4 },
  copy: { fontSize: 13, lineHeight: 19, marginTop: 4 },
  blockAction: { marginTop: 14, alignItems: 'flex-start' },
  emptyState: { paddingVertical: 14, gap: 12 },
  emptyTitle: { fontSize: 15, lineHeight: 21, fontWeight: '800' },
  metricCard: { flexGrow: 1 },
  metricValue: { fontSize: 34, lineHeight: 38, fontWeight: '900', letterSpacing: -1 },
  metricLabel: { fontSize: 14, fontWeight: '800', marginTop: 4 },
  actionCard: { minHeight: 86, flexDirection: 'row', alignItems: 'center', gap: 12 },
  actionCopy: { flex: 1 },
  chevron: { fontSize: 30, fontWeight: '300' },
  pressed: { opacity: 0.7 },
});
