import type { Spec } from '@json-render/core';
import { validateSpec } from '@json-render/core';
import { JSONUIProvider, Renderer, createStandardActionHandlers } from '@json-render/react-native';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { A2UiSurface, A2UiAction, A2UiComponent, AppPackageNativeCapability } from '@/packages/shared/contracts/package';
import type { ProviderSyncSummary, ProviderStatusKey } from '@/src/db/provider-status';
import type { DomainRecordViewModel } from '@/src/domain/renderer';
import { JSON_RENDER_WIDGET_REGISTRY } from '@/src/presentation/json-render-widgets';
import { useLifeOSTheme } from '@/src/theme';

type JsonRenderSurfaceProps = {
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  ui?: A2UiSurface;
  screen?: string;
  records?: DomainRecordViewModel[];
  nativePermissions?: AppPackageNativeCapability['permissions'];
  providerSync?: ProviderSyncSummary | null;
  emptyTitle?: string;
};

type JsonRenderElement = {
  type: string;
  props: Record<string, unknown>;
  children: string[];
  visible?: unknown;
  on?: Record<string, unknown>;
};

type SurfaceScreen = NonNullable<A2UiSurface['screens']>[string];
type Insets = { top: number; bottom: number };

type Palette = {
  canvas: string;
  ink: string;
  muted: string;
  paper: string;
  moss: string;
  mossSoft: string;
  amberSoft: string;
  plumSoft: string;
  blueSoft: string;
};

const lightPalette: Palette = {
  canvas: '#FBF7EE',
  ink: '#182019',
  muted: '#657066',
  paper: '#FFFCF5',
  moss: '#2F7448',
  mossSoft: '#E4F1E8',
  amberSoft: '#F9E7D9',
  plumSoft: '#EFE6ED',
  blueSoft: '#E3EFF3',
};

const darkPalette: Palette = {
  canvas: '#11130F',
  ink: '#F4F0E6',
  muted: '#B9B2A3',
  paper: '#191B16',
  moss: '#A9C891',
  mossSoft: '#263220',
  amberSoft: '#3C291B',
  plumSoft: '#342637',
  blueSoft: '#1F3138',
};

function paletteFor(dark: boolean): Palette {
  // @json-render/react-native standard Card/ListItem components currently
  // hardcode dark text colors internally, so dark surface backgrounds produce
  // unreadable UI. Keep the pure upstream renderer path, but force light
  // surface tokens until dark-mode-capable upstream/custom catalog components
  // replace those standards.
  return dark ? lightPalette : lightPalette;
}

function toneColor(tone: A2UiComponent['tone'], palette: Palette) {
  if (tone === 'moss') return palette.mossSoft;
  if (tone === 'amber') return palette.amberSoft;
  if (tone === 'plum') return palette.plumSoft;
  if (tone === 'blue') return palette.blueSoft;
  return palette.paper;
}

function normalize(text: unknown) {
  return String(text ?? '').toLowerCase();
}

function matchesRecord(record: DomainRecordViewModel, query: NonNullable<A2UiComponent['query']>) {
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

function queryRecords(records: DomainRecordViewModel[], query?: A2UiComponent['query']) {
  if (!query) {
    return records.slice(0, 4);
  }
  return records.filter((record) => matchesRecord(record, query)).slice(0, query.limit ?? 4);
}

function actionRoute(action?: A2UiAction) {
  const route = action?.payload?.route;
  return typeof route === 'string' && route.startsWith('/') ? route : null;
}

function actionBinding(action?: A2UiAction, fallback = '/chat') {
  return {
    action: 'navigate',
    params: {
      screen: actionRoute(action) ?? fallback,
    },
  };
}

function rowRoute(record: DomainRecordViewModel) {
  return `/record/${encodeURIComponent(record.id)}`;
}

function fallbackFor(component: A2UiComponent) {
  if (component.query?.collections?.includes('shopping_item')) return 'No shopping blockers.';
  if (component.query?.collections?.includes('inventory')) return 'No urgent pantry items.';
  if (component.query?.collections?.includes('meal_plan')) return 'Ask Wonder to build tonight.';
  return 'Nothing here yet.';
}

function selectScreen(ui?: A2UiSurface, screen?: string): SurfaceScreen | null {
  if (!ui?.screens) {
    return ui?.components ? { components: ui.components } : null;
  }
  const screenId = screen ?? ui.defaultScreen ?? Object.keys(ui.screens)[0];
  return ui.screens[screenId] ?? ui.screens[ui.defaultScreen ?? ''] ?? Object.values(ui.screens)[0] ?? null;
}

function createBuilder() {
  let next = 0;
  const elements: Record<string, JsonRenderElement> = {};
  const add = (type: string, props: Record<string, unknown> = {}, children: string[] = [], extra: Partial<JsonRenderElement> = {}) => {
    const key = `${type.toLowerCase()}-${next++}`;
    elements[key] = { type, props, children, ...extra };
    return key;
  };
  return { add, elements };
}

function recordIcon(record: DomainRecordViewModel) {
  if (record.collection === 'meal_plan') return '🍽️';
  if (record.collection.includes('shopping')) return '🛒';
  if (record.collection.includes('inventory')) return '🥬';
  return '✨';
}

function addActionButton(add: ReturnType<typeof createBuilder>['add'], action: A2UiAction | undefined, fallback: string) {
  if (!action?.label) {
    return null;
  }
  return add('Button', { label: action.label, variant: 'secondary', size: 'md' }, [], {
    on: { press: actionBinding(action, fallback) },
  });
}

function widgetText(value: unknown, fallback = '') {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function widgetRows(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value)
    ? value.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object' && !Array.isArray(item))
    : [];
}

function widgetLabel(value: unknown, fallback = 'Item') {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const raw = value as Record<string, unknown>;
    return widgetText(raw.title, widgetText(raw.label, widgetText(raw.name, widgetText(raw.permission, widgetText(raw.id, fallback)))));
  }
  return fallback;
}

function widgetDetail(value: unknown, fallback = '') {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const raw = value as Record<string, unknown>;
    return widgetText(raw.subtitle, widgetText(raw.body, widgetText(raw.detail, widgetText(raw.reason, fallback))));
  }
  return fallback;
}

function widgetNumber(value: unknown, fallback = 0): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function widgetActionRoute(value: Record<string, unknown>): string | null {
  const route = widgetText(value.route, widgetText(value.path));
  return route.startsWith('/') ? route : null;
}

function widgetActionUrl(value: Record<string, unknown>): string | null {
  const url = widgetText(value.url, widgetText(value.href, widgetText(value.deeplink)));
  return url ? url : null;
}

function widgetPressBinding(target: Record<string, unknown>): Record<string, unknown> | null {
  const route = widgetActionRoute(target);
  if (route) {
    return { action: 'navigate', params: { screen: route } };
  }
  const url = widgetActionUrl(target);
  if (url) {
    return { action: 'openURL', params: { url } };
  }
  return null;
}

function addWidgetActionButtons(
  add: ReturnType<typeof createBuilder>['add'],
  actions: Record<string, unknown>[],
) {
  const actionButtons = actions.slice(0, 3).flatMap((item) => {
    const press = widgetPressBinding(item);
    if (!press) return [];
    return [add('Button', { label: widgetLabel(item), variant: 'secondary', size: 'sm' }, [], { on: { press } })];
  });
  if (!actionButtons.length) {
    return null;
  }
  return add('Row', { gap: 8, flexWrap: 'wrap' }, actionButtons);
}

function addStandardWidgetCard(
  add: ReturnType<typeof createBuilder>['add'],
  component: A2UiComponent,
  palette: Palette,
  children: string[],
) {
  return add('Card', {
    title: component.title ?? null,
    subtitle: component.subtitle ?? null,
    padding: 18,
    backgroundColor: toneColor(component.tone, palette),
    borderRadius: 18,
    elevated: false,
  }, children);
}

function addStandardDisplayWidget(
  add: ReturnType<typeof createBuilder>['add'],
  component: A2UiComponent,
  palette: Palette,
) {
  const props = component.props ?? {};
  switch (component.widget) {
    case 'widgetCatalog': {
      const itemLabels = [
        ...widgetRows(props.items).map((item) => widgetLabel(item)).filter(Boolean),
        ...((Array.isArray(props.widgets) ? props.widgets : []).filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
          .map((item) => item.replace(/([a-z0-9])([A-Z])/g, '$1 $2'))),
      ];
      const labels = (itemLabels.length ? itemLabels : [
        'Assistant chat',
        'Health connect',
        'Schema editor',
        'Widget catalog',
        'Post card',
        'Poll card',
        'Link preview',
        'Feed list',
        'Kanban board',
        'Chart block',
        'Media block',
        'Map block',
        'Form card',
        'Checklist card',
        'Calendar block',
        'Timeline block',
        'Gallery grid',
        'Data table',
        'Permission card',
        'Provider status',
        'Theme preview',
      ]).slice(0, 32);
      return addStandardWidgetCard(
        add,
        component,
        palette,
        [add('Row', { gap: 8, flexWrap: 'wrap' }, labels.map((item) => add('Chip', { label: item })))],
      );
    }
    case 'postCard': {
      const children: string[] = [];
      const badge = widgetText(props.badge);
      if (badge) children.push(add('Badge', { label: badge, variant: 'warning' }));
      const imageUrl = widgetText(props.imageUrl);
      if (imageUrl) {
        children.push(add('Image', { src: imageUrl, alt: component.title ?? 'Post image', height: 180, borderRadius: 14, resizeMode: 'cover' }));
      }
      const body = widgetText(props.body, 'A package-defined post, note, update, or announcement.');
      children.push(add('Paragraph', { text: body, color: palette.ink, fontSize: 15 }));
      const url = widgetText(props.url);
      if (url) children.push(add('Label', { text: url, color: palette.moss, size: 'sm' }));
      const actions = addWidgetActionButtons(add, widgetRows(props.actions));
      if (actions) children.push(actions);
      return addStandardWidgetCard(add, component, palette, children);
    }
    case 'linkPreview': {
      const target: Record<string, unknown> = { ...props };
      const url = widgetActionUrl(target);
      const host = (() => {
        try {
          return url ? new URL(url).hostname.replace(/^www\./, '') : 'link';
        } catch {
          return 'link';
        }
      })();
      const heroChildren = [
        add('Badge', { label: host, variant: 'info' }),
        add('Paragraph', {
          text: widgetText(props.body, component.subtitle ?? 'A safe preview surface for recipes, docs, posts, and references.'),
          color: palette.ink,
          fontSize: 15,
        }),
        ...(url ? [add('Label', { text: url, color: palette.moss, size: 'sm' })] : []),
      ];
      const hero = add('Container', {
        padding: 16,
        backgroundColor: palette.blueSoft,
        borderRadius: 16,
      }, heroChildren);
      return addStandardWidgetCard(add, component, palette, [
        widgetPressBinding(target)
          ? add('Pressable', {}, [hero], { on: { press: widgetPressBinding(target) ?? undefined } })
          : hero,
      ]);
    }
    case 'feedList': {
      const items = (widgetRows(props.items).length ? widgetRows(props.items) : [{ title: 'No feed items yet', subtitle: 'Ask Wonder to add posts, links, or updates.' }]).slice(0, 8);
      return addStandardWidgetCard(add, component, palette, items.map((item) => {
        const meta = widgetText(item.badge, widgetText(item.status, widgetText(item.date, widgetText(item.when))));
        const subtitle = [meta, widgetDetail(item)].filter(Boolean).join(' · ') || null;
        const press = widgetPressBinding(item);
        return add('ListItem', {
          title: widgetLabel(item),
          subtitle,
          leading: '•',
          showChevron: Boolean(press),
        }, [], press ? { on: { press } } : {});
      }));
    }
    case 'chartBlock': {
      const points = (widgetRows(props.points).length ? widgetRows(props.points) : [{ label: 'A', value: 6 }, { label: 'B', value: 10 }, { label: 'C', value: 4 }])
        .map((point) => ({ label: widgetLabel(point), value: widgetNumber(point.value) }))
        .filter((point) => Number.isFinite(point.value));
      const max = Math.max(1, ...points.map((point) => point.value));
      return addStandardWidgetCard(add, component, palette, points.slice(0, 8).map((point) => add('Column', { gap: 6 }, [
        add('Row', { gap: 10, justifyContent: 'space-between', alignItems: 'center' }, [
          add('Label', { text: point.label, color: palette.ink, bold: true, size: 'sm' }),
          add('Label', { text: String(point.value), color: palette.muted, size: 'sm' }),
        ]),
        add('ProgressBar', { progress: point.value / max, color: palette.moss, trackColor: palette.paper, height: 8 }),
      ])));
    }
    case 'mediaBlock': {
      const target: Record<string, unknown> = { ...props };
      const children: string[] = [];
      const imageUrl = widgetText(props.imageUrl);
      if (imageUrl) {
        children.push(add('Image', { src: imageUrl, alt: component.title ?? 'Media', height: 180, borderRadius: 14, resizeMode: 'cover' }));
      } else {
        children.push(add('Badge', { label: 'Media', variant: 'info' }));
      }
      children.push(add('Paragraph', {
        text: widgetText(props.body, 'Attach or preview media here.'),
        color: palette.ink,
        fontSize: 15,
      }));
      const url = widgetActionUrl(target);
      if (url) children.push(add('Label', { text: url, color: palette.moss, size: 'sm' }));
      const press = widgetPressBinding(target);
      if (press) children.push(add('Button', { label: widgetText(props.cta, 'Open media'), variant: 'secondary', size: 'sm' }, [], { on: { press } }));
      return addStandardWidgetCard(add, component, palette, children);
    }
    case 'mapBlock': {
      const target: Record<string, unknown> = { ...props };
      const children: string[] = [
        add('Badge', { label: widgetText((props as Record<string, unknown>).address, 'Map'), variant: 'info' }),
        add('Paragraph', {
          text: widgetText(props.body, 'Map provider hooks can render stores, trips, homes, routes, or field work.'),
          color: palette.ink,
          fontSize: 15,
        }),
      ];
      const press = widgetPressBinding(target);
      if (press) children.push(add('Button', { label: widgetText(props.cta, 'Open map'), variant: 'secondary', size: 'sm' }, [], { on: { press } }));
      return addStandardWidgetCard(add, component, palette, children);
    }
    case 'calendarBlock': {
      const events = (widgetRows(props.events).length ? widgetRows(props.events) : [{ title: 'Dinner plan', subtitle: 'Tonight' }, { title: 'Shopping', subtitle: 'Tomorrow' }]).slice(0, 7);
      return addStandardWidgetCard(add, component, palette, events.map((event) => add('ListItem', {
        title: widgetLabel(event),
        subtitle: [widgetText(event.date, widgetText(event.when)), widgetDetail(event)].filter(Boolean).join(' · ') || null,
        leading: '📅',
      })));
    }
    case 'timelineBlock': {
      const items = (widgetRows(props.items).length ? widgetRows(props.items) : [{ title: 'Started', subtitle: 'Created from package config' }, { title: 'Next', subtitle: 'Ask Wonder to add events' }]).slice(0, 10);
      return addStandardWidgetCard(add, component, palette, items.map((item) => add('ListItem', {
        title: widgetLabel(item),
        subtitle: widgetDetail(item, widgetText(item.time)),
        leading: '•',
      })));
    }
    case 'galleryGrid': {
      const items = (widgetRows(props.items).length ? widgetRows(props.items) : [{ title: 'Image' }, { title: 'Clip' }, { title: 'Doc' }, { title: 'Audio' }]).slice(0, 8);
      return addStandardWidgetCard(add, component, palette, [
        add('Row', { gap: 10, flexWrap: 'wrap' }, items.map((item, index) => {
          const imageUrl = widgetText(item.imageUrl, widgetText(item.url));
          const tileChildren = imageUrl
            ? [
                add('Image', { src: imageUrl, alt: widgetLabel(item), height: 96, borderRadius: 12, resizeMode: 'cover' }),
                add('Label', { text: widgetLabel(item), color: palette.ink, bold: true, size: 'sm' }),
              ]
            : [add('Chip', { label: `${widgetText(item.emoji, '◼︎')} ${widgetLabel(item)}`, backgroundColor: index % 2 === 0 ? palette.plumSoft : palette.blueSoft })];
          const press = widgetPressBinding(item);
          const tile = add('Container', { padding: 6, backgroundColor: palette.paper, borderRadius: 14 }, tileChildren);
          return press ? add('Pressable', {}, [tile], { on: { press } }) : tile;
        })),
      ]);
    }
    case 'dataTable': {
      const columns = (widgetRows(props.columns).length ? widgetRows(props.columns) : [
        { key: 'name', label: 'Name' },
        { key: 'status', label: 'Status' },
        { key: 'owner', label: 'Owner' },
      ]).slice(0, 5).map((column, index) => ({
        key: widgetText(column.key, widgetText(column.field, widgetText(column.id, widgetText(column.name, `column_${index}`)))).toLowerCase().replace(/[^a-z0-9]+/g, '_'),
        title: widgetLabel(column, `Column ${index + 1}`),
      }));
      const items = (widgetRows(props.items).length ? widgetRows(props.items) : [{ name: 'Sample', status: 'Ready', owner: 'Wonder' }]).slice(0, 6);
      const header = add('Row', { gap: 8 }, columns.map((column) => add('Container', { flex: 1 }, [
        add('Label', { text: column.title, color: palette.muted, bold: true, size: 'xs' }),
      ])));
      const rows = items.map((item, index) => {
        const press = widgetPressBinding(item);
        const row = add('Container', { paddingVertical: 10 }, [
          add('Row', { gap: 8 }, columns.map((column, columnIndex) => add('Container', { flex: 1 }, [
            add('Paragraph', {
              text: widgetText(item[column.key], columnIndex === 0 ? widgetLabel(item) : '—'),
              color: palette.ink,
              fontSize: 14,
              numberOfLines: 3,
            }),
          ]))),
        ]);
        return press ? add('Pressable', {}, [row], { on: { press } }) : add('Container', { margin: 0 }, [row], { visible: true });
      });
      const tableChildren: string[] = [header];
      rows.forEach((row, index) => {
        tableChildren.push(add('Divider', { color: palette.blueSoft, margin: 0 }));
        tableChildren.push(row);
        if (index === rows.length - 1) {
          tableChildren.push(add('Divider', { color: palette.blueSoft, margin: 0 }));
        }
      });
      return addStandardWidgetCard(add, component, palette, tableChildren);
    }
    case 'themePreview': {
      const colorSource = props.colors && typeof props.colors === 'object' && !Array.isArray(props.colors)
        ? Object.entries(props.colors as Record<string, unknown>).filter(([, value]) => typeof value === 'string' && value.trim())
        : [];
      const swatches = (colorSource.length ? colorSource : [
        ['primary', '#2F7448'],
        ['accent', '#F3B15E'],
        ['calm', '#B9DCE8'],
        ['ink', '#241C16'],
      ]) as Array<[string, string]>;
      const children: string[] = [
        add('Row', { gap: 8, flexWrap: 'wrap' }, swatches.map(([name, value]) => add('Chip', {
          label: `${name}: ${value}`,
          backgroundColor: value,
        }))),
      ];
      const mood = widgetText(props.mood);
      if (mood) children.push(add('Paragraph', { text: mood, color: palette.muted, fontSize: 14 }));
      const density = widgetText(props.density);
      if (density) children.push(add('Badge', { label: density, variant: 'success' }));
      return addStandardWidgetCard(add, component, palette, children);
    }
    default:
      return null;
  }
}

function addTextBlock(add: ReturnType<typeof createBuilder>['add'], component: A2UiComponent, palette: Palette) {
  const children = [
    add('Heading', { text: component.title ?? 'Section', level: 'h3', color: palette.ink }),
  ];
  if (component.subtitle) {
    children.push(add('Paragraph', { text: component.subtitle, color: palette.muted, fontSize: 15 }));
  }
  const button = addActionButton(add, component.action, '/chat');
  if (button) children.push(button);
  return add('Card', {
    title: null,
    subtitle: null,
    padding: 18,
    backgroundColor: toneColor(component.tone, palette),
    borderRadius: 18,
    elevated: false,
  }, children);
}

function addActionBlock(add: ReturnType<typeof createBuilder>['add'], component: A2UiComponent, palette: Palette) {
  return add('Card', {
    title: component.title ?? component.action?.label ?? 'Open',
    subtitle: component.subtitle ?? null,
    padding: 18,
    backgroundColor: toneColor(component.tone, palette),
    borderRadius: 18,
    elevated: false,
  }, [
    add('Button', { label: component.action?.label ?? 'Open', variant: 'secondary', size: 'md' }, [], {
      on: { press: actionBinding(component.action, '/chat') },
    }),
  ]);
}

function addMetricBlock(add: ReturnType<typeof createBuilder>['add'], component: A2UiComponent, records: DomainRecordViewModel[], palette: Palette) {
  const rows = queryRecords(records, component.query);
  return add('Card', {
    title: component.title ?? 'Metric',
    subtitle: component.subtitle ?? null,
    padding: 18,
    backgroundColor: toneColor(component.tone, palette),
    borderRadius: 18,
    elevated: false,
  }, [
    add('Heading', { text: String(rows.length), level: 'h1', color: palette.ink }),
  ]);
}

function addRecordListBlock(add: ReturnType<typeof createBuilder>['add'], component: A2UiComponent, records: DomainRecordViewModel[], palette: Palette) {
  const rows = queryRecords(records, component.query);
  const children: string[] = [];
  const button = addActionButton(add, component.action, '/chat');
  if (rows.length) {
    for (const row of rows) {
      children.push(add('ListItem', {
        title: row.title,
        subtitle: row.body || row.meta || row.status,
        leading: recordIcon(row),
        trailing: null,
        showChevron: true,
      }, [], {
        on: { press: actionBinding({ kind: 'propose', payload: { route: rowRoute(row) } }, rowRoute(row)) },
      }));
    }
  } else {
    children.push(add('Paragraph', { text: fallbackFor(component), color: palette.muted, fontSize: 15 }));
  }
  if (button) children.push(button);
  return add('Card', {
    title: component.title ?? 'Records',
    subtitle: component.subtitle ?? null,
    padding: 14,
    backgroundColor: palette.paper,
    borderRadius: 18,
    elevated: false,
  }, children);
}

function providerKeyFromComponent(component: A2UiComponent): ProviderStatusKey {
  const raw = component.props?.provider;
  if (raw === 'local' || raw === 'notion' || raw === 'google_sheets' || raw === 'summary') return raw;
  return 'summary';
}

function addSurfaceComponent(
  add: ReturnType<typeof createBuilder>['add'],
  component: A2UiComponent,
  records: DomainRecordViewModel[],
  palette: Palette,
  nativePermissions?: AppPackageNativeCapability['permissions'],
  providerSync?: ProviderSyncSummary | null,
) {
  if (component.kind === 'widget') {
    const standardWidgetKinds = new Set<string>([
      'widgetCatalog',
      'postCard',
      'linkPreview',
      'feedList',
      'chartBlock',
      'mediaBlock',
      'mapBlock',
      'calendarBlock',
      'timelineBlock',
      'galleryGrid',
      'dataTable',
      'themePreview',
    ]);
    if (component.widget && standardWidgetKinds.has(component.widget)) {
      const rendered = addStandardDisplayWidget(add, component, palette);
      if (rendered) return rendered;
    }
    const typeByWidget: Record<string, string> = {
      assistantChat: 'AssistantChatWidget',
      healthConnect: 'HealthConnectWidget',
      schemaEditor: 'SchemaEditorWidget',
      pollCard: 'PollCardWidget',
      kanbanBoard: 'KanbanBoardWidget',
      formCard: 'FormCardWidget',
      checklistCard: 'ChecklistCardWidget',
      permissionCard: 'PermissionCardWidget',
      providerStatus: 'ProviderStatusWidget',
    };
    const widgetType = component.widget ? typeByWidget[component.widget] : null;
    if (widgetType) {
      return add(widgetType, {
        title: component.title,
        subtitle: component.subtitle,
        ...(component.widget === 'permissionCard' && component.props?.permissions === undefined && nativePermissions ? { permissions: nativePermissions } : {}),
        ...(component.widget === 'providerStatus' && providerSync ? { providerStatus: providerSync.providers[providerKeyFromComponent(component)] } : {}),
        ...(component.props ?? {}),
      });
    }
  }
  if (component.kind === 'recordList') return addRecordListBlock(add, component, records, palette);
  if (component.kind === 'metric') return addMetricBlock(add, component, records, palette);
  if (component.kind === 'action') return addActionBlock(add, component, palette);
  return addTextBlock(add, component, palette);
}

function composeJsonRenderSpec(props: JsonRenderSurfaceProps, palette: Palette, insets: Insets): Spec {
  const screen = selectScreen(props.ui, props.screen);
  const components = screen?.components ?? [];
  const { add, elements } = createBuilder();
  const topGap = Math.max(44, insets.top + 16);
  const bottomGap = Math.max(42, insets.bottom + 22);
  const contentChildren = [
    add('Spacer', { size: topGap }),
    ...(props.eyebrow ? [add('Label', { text: props.eyebrow, color: palette.moss, bold: true, size: 'md' })] : []),
    add('Heading', { text: screen?.title ?? props.title ?? 'Wonder', level: 'h1', color: palette.ink }),
  ];
  const subtitle = screen?.subtitle ?? props.subtitle;
  if (subtitle) {
    contentChildren.push(add('Paragraph', { text: subtitle, color: palette.muted, fontSize: 18 }));
  }
  if (components.length) {
    for (const component of components) {
      contentChildren.push(addSurfaceComponent(add, component, props.records ?? [], palette, props.nativePermissions, props.providerSync));
    }
  } else {
    contentChildren.push(add('Card', {
      title: props.emptyTitle ?? 'Nothing configured yet.',
      subtitle: 'Ask Wonder to create or edit this surface.',
      padding: 18,
      backgroundColor: palette.paper,
      borderRadius: 18,
      elevated: false,
    }));
  }
  contentChildren.push(add('Spacer', { size: bottomGap }));
  const column = add('Column', {
    gap: 14,
    padding: 16,
    flex: 1,
  }, contentChildren);
  const scroll = add('ScrollContainer', { padding: 0, backgroundColor: palette.canvas, horizontal: false, showsScrollIndicator: true }, [column]);
  const root = add('SafeArea', { backgroundColor: palette.canvas }, [scroll]);
  return { root, elements } as Spec;
}

function assertJsonRenderSpec(spec: Spec): Spec {
  const result = validateSpec(spec);
  if (!result.valid) {
    throw new Error(`Invalid json-render spec: ${result.issues.map((issue) => issue.message).join('; ')}`);
  }
  return spec;
}

export function JsonRenderSurface(props: JsonRenderSurfaceProps) {
  const router = useRouter();
  const theme = useLifeOSTheme();
  const insets = useSafeAreaInsets();
  const palette = paletteFor(theme.dark);
  const spec = useMemo(() => assertJsonRenderSpec(composeJsonRenderSpec(props, palette, insets)), [insets, palette, props]);
  const handlers = useMemo(() => createStandardActionHandlers({
    navigate: (screen) => router.push(screen as never),
    goBack: () => router.back(),
  }), [router]);

  return (
    <JSONUIProvider navigate={(path) => router.push(path as never)} handlers={handlers} registry={JSON_RENDER_WIDGET_REGISTRY}>
      <Renderer spec={spec} includeStandard registry={JSON_RENDER_WIDGET_REGISTRY} />
    </JSONUIProvider>
  );
}
