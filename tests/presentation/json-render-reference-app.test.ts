import { readFileSync } from 'node:fs';

import { beforeAll, describe, expect, it, vi } from 'vitest';

import type { AppPackage, A2UiSurface } from '@/packages/shared/contracts/package';
import type { CanonicalRecord } from '@/packages/shared/contracts/records';
import { loadAppPackage } from '@/src/domain/package-loader';
import { recordsToViews } from '@/src/domain/renderer';

vi.mock('expo-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    back: vi.fn(),
  }),
}));

vi.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0 }),
}));

vi.mock('@json-render/react-native', () => ({
  JSONUIProvider: ({ children }: { children: unknown }) => children,
  Renderer: () => null,
  createStandardActionHandlers: () => ({}),
}));

vi.mock('@/src/presentation/json-render-widgets', () => ({
  JSON_RENDER_WIDGET_REGISTRY: {},
}));

vi.mock('@/src/theme', () => ({
  useLifeOSTheme: () => ({ dark: false }),
}));

let buildJsonRenderSpec: typeof import('@/src/presentation/json-render-surface').buildJsonRenderSpec;

beforeAll(async () => {
  ({ buildJsonRenderSpec } = await import('@/src/presentation/json-render-surface'));
});

describe('reference app renderer', () => {
  it('composes today, chores, and household screens from compiled JSON', () => {
    const runtime = loadReferenceRuntime('1.0.0');
    const records = loadReferenceRecords();

    for (const screen of ['today', 'chores', 'household'] as const) {
      const spec = buildJsonRenderSpec({
        title: runtime.activeManifest.label,
        ui: runtime.activeManifest.ui,
        screen,
        records,
      });
      expect(spec.root).toBeTruthy();
      expect(specText(spec)).toContain(screen === 'today' ? 'Today' : screen === 'chores' ? 'Chores' : 'Household');
    }
  });

  it('uses package empty-state copy and renders 1.1.0 chore metadata', () => {
    const runtimeV1 = loadReferenceRuntime('1.0.0');
    const runtimeV11 = loadReferenceRuntime('1.1.0');
    const records = loadReferenceRecords();

    const emptySpec = buildJsonRenderSpec({
      title: runtimeV1.activeManifest.label,
      ui: runtimeV1.activeManifest.ui,
      screen: 'chores',
      records: records.filter((record) => record.collection !== 'chore'),
    });
    expect(specText(emptySpec)).toContain('No chores ready yet.');

    const v11Spec = buildJsonRenderSpec({
      title: runtimeV11.activeManifest.label,
      ui: runtimeV11.activeManifest.ui,
      screen: 'chores',
      records,
    });
    expect(specText(v11Spec)).toContain('15 min · Daily');
    expect(specText(v11Spec)).toContain('30 min · Weekly');
  });

  it('shows a neutral unsupported state and survives malformed widget props', () => {
    const spec = buildJsonRenderSpec({
      title: 'Custom',
      screen: 'custom',
      records: [],
      ui: {
        schemaVersion: 'a2ui.v0_9',
        defaultScreen: 'custom',
        screens: {
          custom: {
            title: 'Custom',
            components: [
              { kind: 'widget', widget: 'unknown_widget', title: 'Mystery block' } as any,
              { kind: 'widget', widget: 'feedList', title: 'Feed', props: { items: 'bad' } } as any,
              { kind: 'widget', widget: 'dataTable', title: 'Table', props: { items: 'bad', columns: 'bad' } } as any,
            ],
          },
        },
      } satisfies A2UiSurface,
    });

    const text = specText(spec);
    expect(text).toContain('Mystery block');
    expect(text).toContain('This package component is unavailable in this runtime.');
    expect(text).toContain('No feed items yet');
    expect(text).toContain('Sample');
  });

  it('keeps runtime renderer generic and free of reference collection hardcodes', () => {
    const source = [
      'src/presentation/json-render-route.tsx',
      'src/presentation/json-render-surface.tsx',
      'src/presentation/json-render-widgets.tsx',
      'src/domain/renderer.tsx',
    ].map((path) => readFileSync(path, 'utf8')).join('\n');

    expect(source).not.toMatch(/\breference-app\b/);
    expect(source).not.toMatch(/\bchore\b/);
    expect(source).not.toMatch(/\bassignment\b/);
    expect(source).not.toMatch(/\bhousehold_member\b/);
    expect(source).not.toMatch(/\bcompletion\b/);
    expect(source).not.toMatch(/\bshopping_item\b/);
    expect(source).not.toMatch(/\binventory\b/);
    expect(source).not.toMatch(/\bmeal_plan\b/);
    expect(source).not.toMatch(/\bpantry\b/);
    expect(source).not.toMatch(/\bshopping\b/);
    expect(source).not.toMatch(/\bmeal\b/);
    expect(source).not.toContain('Ask Wonder');
    expect(source).not.toContain('WonderFood');
    expect(source).not.toMatch(/\bfood\b/);
    expect(source).not.toMatch(/\bWonder\b/);
  });
});

function loadReferenceRuntime(version: '1.0.0' | '1.1.0') {
  const parsed = JSON.parse(
    readFileSync(`tests/fixtures/app-packages/reference-app/compiled/reference-app-${version}.package.json`, 'utf8'),
  ) as AppPackage;
  return loadAppPackage(parsed);
}

function loadReferenceRecords() {
  const parsed = JSON.parse(
    readFileSync('tests/fixtures/app-packages/reference-app/fixtures/records.json', 'utf8'),
  ) as CanonicalRecord[];
  return recordsToViews(parsed);
}

function specText(spec: { elements: Record<string, { props?: Record<string, unknown> }> }) {
  return Object.values(spec.elements)
    .flatMap((element) => Object.values(element.props ?? {}))
    .filter((value): value is string => typeof value === 'string' && value.length > 0)
    .join(' ');
}
