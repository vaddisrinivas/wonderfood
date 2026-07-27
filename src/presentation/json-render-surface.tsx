import type { Spec } from '@json-render/core';
import { validateSpec } from '@json-render/core';
import { JSONUIProvider, Renderer } from '@json-render/react-native';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';

import type { PackagePresentationUi, PackageUiAction, PackageUiComponent } from '@/packages/shared/contracts/package';
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

type JsonRenderElement = {
  type: string;
  props: Record<string, unknown>;
  children: string[];
  visible?: unknown;
  on?: Record<string, unknown>;
};

type SurfaceScreen = NonNullable<PackagePresentationUi['screens']>[string];

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
  canvas: '#F6F7F4',
  ink: '#182019',
  muted: '#657066',
  paper: '#FFFFFF',
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

function toneColor(tone: PackageUiComponent['tone'], palette: Palette) {
  if (tone === 'moss') return palette.mossSoft;
  if (tone === 'amber') return palette.amberSoft;
  if (tone === 'plum') return palette.plumSoft;
  if (tone === 'blue') return palette.blueSoft;
  return palette.paper;
}

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

function actionRoute(action?: PackageUiAction) {
  const route = action?.payload?.route;
  return typeof route === 'string' && route.startsWith('/') ? route : null;
}

function actionBinding(action?: PackageUiAction, fallback = '/chat') {
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

function fallbackFor(component: PackageUiComponent) {
  if (component.query?.collections?.includes('shopping_item')) return 'No shopping blockers.';
  if (component.query?.collections?.includes('inventory')) return 'No urgent pantry items.';
  if (component.query?.collections?.includes('meal_plan')) return 'Ask Wonder to build tonight.';
  return 'Nothing here yet.';
}

function selectScreen(ui?: PackagePresentationUi, screen?: string): SurfaceScreen | null {
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

function addActionButton(add: ReturnType<typeof createBuilder>['add'], action: PackageUiAction | undefined, fallback: string) {
  if (!action?.label) {
    return null;
  }
  return add('Button', { label: action.label, variant: 'secondary', size: 'lg' }, [], {
    on: { press: actionBinding(action, fallback) },
  });
}

function addTextBlock(add: ReturnType<typeof createBuilder>['add'], component: PackageUiComponent, palette: Palette) {
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

function addActionBlock(add: ReturnType<typeof createBuilder>['add'], component: PackageUiComponent, palette: Palette) {
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

function addMetricBlock(add: ReturnType<typeof createBuilder>['add'], component: PackageUiComponent, records: DomainRecordViewModel[], palette: Palette) {
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

function addRecordListBlock(add: ReturnType<typeof createBuilder>['add'], component: PackageUiComponent, records: DomainRecordViewModel[], palette: Palette) {
  const rows = queryRecords(records, component.query);
  const children: string[] = [];
  const button = addActionButton(add, component.action, '/chat');
  if (button) children.push(button);
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
  return add('Card', {
    title: component.title ?? 'Records',
    subtitle: component.subtitle ?? null,
    padding: 18,
    backgroundColor: toneColor(component.tone, palette),
    borderRadius: 18,
    elevated: false,
  }, children);
}

function addSurfaceComponent(add: ReturnType<typeof createBuilder>['add'], component: PackageUiComponent, records: DomainRecordViewModel[], palette: Palette) {
  if (component.kind === 'recordList') return addRecordListBlock(add, component, records, palette);
  if (component.kind === 'metric') return addMetricBlock(add, component, records, palette);
  if (component.kind === 'action') return addActionBlock(add, component, palette);
  return addTextBlock(add, component, palette);
}

function buildSpec(props: JsonRenderSurfaceProps, palette: Palette): Spec {
  const screen = selectScreen(props.ui, props.screen);
  const components = screen?.components ?? [];
  const { add, elements } = createBuilder();
  const contentChildren = [
    ...(props.eyebrow ? [add('Label', { text: props.eyebrow, color: palette.moss, bold: true, size: 'md' })] : []),
    add('Heading', { text: screen?.title ?? props.title ?? 'Wonder', level: 'h1', color: palette.ink }),
  ];
  const subtitle = screen?.subtitle ?? props.subtitle;
  if (subtitle) {
    contentChildren.push(add('Paragraph', { text: subtitle, color: palette.muted, fontSize: 18 }));
  }
  if (components.length) {
    for (const component of components) {
      contentChildren.push(addSurfaceComponent(add, component, props.records ?? [], palette));
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
  const column = add('Column', { gap: 16, padding: 18, flex: 1 }, contentChildren);
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
  const palette = paletteFor(theme.dark);
  const spec = useMemo(() => assertJsonRenderSpec(buildSpec(props, palette)), [palette, props]);

  return (
    <JSONUIProvider navigate={(path) => router.push(path as never)}>
      <Renderer spec={spec} includeStandard />
    </JSONUIProvider>
  );
}
