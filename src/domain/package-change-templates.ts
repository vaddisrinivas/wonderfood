import type { AppPackage, A2UiComponent, PackagePresentationSpec } from '@/packages/shared/contracts/package';
import type { AppPackageChangeRequest } from '@/src/db/app-package-registry';

type PackageChangeName = ReturnType<typeof derivePackageChangeName>;

export function buildSafePackageChangeRequest(active: AppPackage, prompt: string): AppPackageChangeRequest {
  const intent = classifyPackageChangeIntent(prompt);
  const name = derivePackageChangeName(prompt);
  const presentation = active.presentation;
  if (!presentation) throw new Error('Active package has no presentation section.');

  if (intent === 'theme') return buildThemeChange(active, presentation, name);
  if (intent === 'workflow') return buildWorkflowChange(active, presentation, name);
  return buildTableScreenChange(active, presentation, name);
}

function buildTableScreenChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
): AppPackageChangeRequest {
  if (active.collections[name.collectionId]) throw new Error(`Collection already exists: ${name.collectionId}`);
  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      {
        op: 'add',
        path: `/collections/${name.collectionId}`,
        value: {
          id: name.collectionId,
          fields: {
            id: { type: 'text', required: true, indexed: true },
            title: { type: 'text', required: true, indexed: true },
            body: { type: 'text' },
            status: { type: 'text', indexed: true },
            tags: { type: 'json' },
            updated_at: { type: 'timestamp', indexed: true },
            properties: { type: 'json' },
          },
        },
      },
      { op: 'add', path: `/queries/${name.collectionId}`, value: { from: name.collectionId, orderBy: [{ field: 'updated_at', direction: 'desc' }], limit: 24 } },
      { op: 'add', path: `/views/${name.collectionId}`, value: { id: name.collectionId, query: name.collectionId, mode: 'list', fields: ['title', 'status', 'body', 'tags'] } },
      { op: 'add', path: '/presentation/surfaces/-', value: { id: name.surfaceId, label: name.label, collections: [name.collectionId], views: [name.collectionId] } },
      ...buildUiScreenPatches(presentation, name, [
        {
          kind: 'widget',
          widget: 'postCard',
          id: `${name.collectionId}_hero`,
          title: `New ${name.label}`,
          subtitle: 'Ready for records, links, posts, and workflows.',
          props: { body: 'This screen was added through a reviewable AppPackage diff.' },
          tone: 'moss',
        },
        {
          kind: 'recordList',
          id: `${name.collectionId}_records`,
          title: `${name.label} records`,
          subtitle: 'Data comes from the new collection.',
          query: { collections: [name.collectionId], limit: 12 },
        },
      ]),
    ],
  };
}

function buildThemeChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
): AppPackageChangeRequest {
  const visualIdentity = {
    ...(presentation.visualIdentity ?? {}),
    schemaVersion: 'wonder.visual-identity.v1',
    mood: 'warm, minimal, alive',
    colors: {
      background: '#FFF7EA',
      card: '#FFFFFF',
      text: '#241C16',
      primary: '#2F7448',
      accent: '#F3B15E',
      calm: '#B9DCE8',
    },
    radius: { card: 24, chip: 999 },
    density: 'compact-cute',
  };
  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      { op: presentation.visualIdentity ? 'replace' : 'add', path: '/presentation/visualIdentity', value: visualIdentity },
      ...buildUiScreenPatches(presentation, { ...name, screenId: `${name.screenId}_theme`, surfaceId: `${name.surfaceId}_theme` }, [
        { kind: 'widget', widget: 'themePreview', id: `${name.screenId}_theme_preview`, title: `${name.label} theme`, subtitle: 'Tokenized visual identity preview.' },
        { kind: 'text', id: `${name.screenId}_theme_note`, title: 'Design tokens', subtitle: 'Background, card, text, primary, accent, radius, and density now live in package config.' },
      ]),
    ],
  };
}

function buildWorkflowChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
): AppPackageChangeRequest {
  const ruleId = `${name.collectionId}_workflow_rule`;
  if (active.rules.some((rule) => rule.id === ruleId)) throw new Error(`Workflow rule already exists: ${ruleId}`);
  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      {
        op: 'add',
        path: '/rules/-',
        value: {
          id: ruleId,
          trigger: { kind: 'query_transition', query: 'expiring_inventory', transition: 'enter' },
          when: { '==': [{ var: 'collection' }, 'inventory'] },
          effect: { kind: 'propose_operation', operation: { kind: 'custom', tool: 'food.dinner.suggest' } },
          mode: 'suggest',
          maxRunsPerEvent: 1,
        },
      },
      ...buildUiScreenPatches(presentation, { ...name, screenId: `${name.screenId}_workflow`, surfaceId: `${name.surfaceId}_workflow` }, [
        { kind: 'widget', widget: 'postCard', id: `${name.screenId}_workflow_hero`, title: `${name.label} workflow`, subtitle: 'Rule added as suggestion-only.', props: { body: 'When inventory enters the expiring set, Wonder can suggest dinner instead of silently mutating data.' } },
        { kind: 'widget', widget: 'feedList', id: `${name.screenId}_workflow_feed`, title: 'Workflow receipts', subtitle: 'Approvals and receipts remain visible only when you open this control surface.' },
      ]),
    ],
  };
}

function buildUiScreenPatches(
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
  components: A2UiComponent[],
): AppPackageChangeRequest['patch'] {
  const screen = {
    title: name.label,
    subtitle: 'AI-created surface. Edit its package JSON or ask Wonder for another change.',
    components,
  };
  if (!presentation.ui) {
    return [{
      op: 'add',
      path: '/presentation/ui',
      value: { schemaVersion: 'a2ui.v0_9', defaultScreen: name.screenId, screens: { [name.screenId]: screen }, components: [] },
    }];
  }
  if (!presentation.ui.screens) {
    return [
      { op: 'add', path: '/presentation/ui/screens', value: {} },
      { op: 'add', path: `/presentation/ui/screens/${name.screenId}`, value: screen },
    ];
  }
  if (presentation.ui.screens[name.screenId]) throw new Error(`Screen already exists: ${name.screenId}`);
  return [{ op: 'add', path: `/presentation/ui/screens/${name.screenId}`, value: screen }];
}

function classifyPackageChangeIntent(prompt: string): 'table' | 'theme' | 'workflow' {
  const value = prompt.toLowerCase();
  if (/\b(theme|color|style|visual|design|cute|density|card|cards)\b/.test(value)) return 'theme';
  if (/\b(rule|workflow|when|expires|expire|automate|suggest|remind)\b/.test(value)) return 'workflow';
  return 'table';
}

function derivePackageChangeName(prompt: string) {
  const clean = prompt
    .replace(/\b(add|create|make|new|table|screen|surface|collection|with|for|a|an|the|theme|workflow|rule|when|suggest|automate|remind)\b/gi, ' ')
    .replace(/[^a-z0-9 ]/gi, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 3)
    .join(' ');
  const label = titleCase(clean || 'Notes');
  const slug = (clean || 'notes').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'notes';
  return {
    label,
    collectionId: `ai_${slug}`,
    screenId: `ai_${slug}`,
    surfaceId: `ai_${slug}`,
  };
}

function versionPatch(version: string): AppPackageChangeRequest['patch'][number] {
  return { op: 'replace', path: '/version', value: nextRuntimeVersion(version) };
}

function titleCase(value: string) {
  return value.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());
}

function nextRuntimeVersion(version: string) {
  return `${version.replace(/\+ai\.[a-z0-9]+$/i, '')}+ai.${Date.now().toString(36)}`;
}
