import { sha256 } from 'js-sha256';

import type {
  A2UiComponent,
  AppPackage,
  AppPackageNativeCapability,
  AppPackagePermissionDeclaration,
  AppPackageV3,
  PackagePresentationSpec,
} from '@/packages/shared/contracts/package';
import type { AppPackageChangeRequest } from '@/src/db/app-package-registry';

type PackageChangeName = ReturnType<typeof derivePackageChangeName>;
type PackageChangeIntent =
  | 'table'
  | 'theme'
  | 'workflow'
  | 'form'
  | 'board'
  | 'feed'
  | 'poll'
  | 'calendar'
  | 'timeline'
  | 'gallery'
  | 'media'
  | 'link'
  | 'map'
  | 'chart'
  | 'native'
  | 'screen';

export function buildSafePackageChangeRequest(active: AppPackage, prompt: string): AppPackageChangeRequest {
  const intent = classifyPackageChangeIntent(prompt);
  const name = derivePackageChangeName(prompt);
  const presentation = active.presentation;
  if (!presentation) throw new Error('Active package has no presentation section.');

  if (intent === 'theme') return buildThemeChange(active, presentation, name);
  if (intent === 'workflow') return buildWorkflowChange(active, presentation, name);
  if (intent === 'native') return buildNativeCapabilityChange(active, presentation, name, prompt);
  if (intent !== 'table') return buildWidgetScreenChange(active, presentation, name, intent);
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

function buildWidgetScreenChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
  intent: Exclude<PackageChangeIntent, 'table' | 'theme' | 'workflow'>,
): AppPackageChangeRequest {
  const collectionIntent = intent === 'screen' ? 'table' : intent;
  if (active.collections[name.collectionId]) throw new Error(`Collection already exists: ${name.collectionId}`);
  const widget = widgetForIntent(intent);
  const viewMode = viewModeForIntent(intent);
  const fields = fieldsForIntent(intent);
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
          fields,
        },
      },
      { op: 'add', path: `/queries/${name.collectionId}`, value: { from: name.collectionId, orderBy: [{ field: 'updated_at', direction: 'desc' }], limit: 24 } },
      { op: 'add', path: `/views/${name.collectionId}`, value: { id: name.collectionId, query: name.collectionId, mode: viewMode, fields: Object.keys(fields).slice(0, 6) } },
      { op: 'add', path: '/presentation/surfaces/-', value: { id: name.surfaceId, label: name.label, collections: [name.collectionId], views: [name.collectionId] } },
      ...buildUiScreenPatches(presentation, name, componentsForIntent(name, intent, collectionIntent, widget)),
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

function buildNativeCapabilityChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
  prompt: string,
): AppPackageChangeRequest {
  if (active.schemaVersion !== 'wonder.app-package.v3') {
    throw new Error('Native capability changes require AppPackage V3 contract locks.');
  }

  const nativeCapabilities = mergeNativeCapabilityRequest(active.nativeCapabilities, prompt);
  const pinnedAt = new Date().toISOString();
  const nextLock: AppPackageV3['contractLock'] = {
    ...active.contractLock,
    pinnedAt,
    nativeCapabilities,
    checksum: '',
  };
  nextLock.checksum = hashValue({
    schemaVersion: nextLock.schemaVersion,
    algorithm: nextLock.algorithm,
    pinnedAt: nextLock.pinnedAt,
    dependencyPins: nextLock.dependencyPins,
    nativeCapabilities: nextLock.nativeCapabilities,
  });

  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      { op: 'replace', path: '/nativeCapabilities', value: nativeCapabilities },
      { op: 'replace', path: '/contractLock/nativeCapabilities', value: nativeCapabilities },
      { op: 'replace', path: '/contractLock/pinnedAt', value: pinnedAt },
      { op: 'replace', path: '/contractLock/checksum', value: nextLock.checksum },
      ...buildUiScreenPatches(presentation, { ...name, screenId: `${name.screenId}_permissions`, surfaceId: `${name.surfaceId}_permissions` }, [
        {
          kind: 'widget',
          widget: 'permissionCard',
          id: `${name.screenId}_permission_review`,
          title: `${name.label} capability`,
          subtitle: 'Native permissions and app intents are declared in package config before the shell can request them.',
          props: {
            permissions: nativeCapabilities.permissions ?? [],
            intents: nativeCapabilities.intents ?? [],
          },
          tone: 'amber',
        },
        {
          kind: 'text',
          id: `${name.screenId}_permission_note`,
          title: 'Locked package diff',
          subtitle: 'This changes the native capability envelope and contract checksum; runtime code still cannot grant OS permissions by itself.',
        },
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

function classifyPackageChangeIntent(prompt: string): PackageChangeIntent {
  const value = prompt.toLowerCase();
  if (/\b(theme|color|style|visual|design|cute|density|card|cards)\b/.test(value)) return 'theme';
  if (/\b(rule|workflow|when|expires|expire|automate|suggest|remind)\b/.test(value)) return 'workflow';
  if (/\b(permission|permissions|capability|capabilities|camera|photo library|photos?|voice|okay google|google assistant|shortcut|deep[- ]?link|background|file open|open file|health connect|share sheet|share intent)\b/.test(value)) return 'native';
  if (/\b(form|input|survey|submit|fields?)\b/.test(value)) return 'form';
  if (/\b(board|kanban|pipeline|status board|columns?)\b/.test(value)) return 'board';
  if (/\b(feed|posts?|updates?|social|comments?)\b/.test(value)) return 'feed';
  if (/\b(poll|vote|voting|ballot|choice)\b/.test(value)) return 'poll';
  if (/\b(calendar|schedule|booking|appointment|events?)\b/.test(value)) return 'calendar';
  if (/\b(timeline|history|milestone|log|journey)\b/.test(value)) return 'timeline';
  if (/\b(gallery|photos?|images?|album|grid)\b/.test(value)) return 'gallery';
  if (/\b(media|video|audio|youtube|song|clip)\b/.test(value)) return 'media';
  if (/\b(link|url|preview|bookmark|website|webpage)\b/.test(value)) return 'link';
  if (/\b(map|location|place|places|route|store finder|geo)\b/.test(value)) return 'map';
  if (/\b(chart|graph|analytics|trend|dashboard metric|report)\b/.test(value)) return 'chart';
  if (/\b(screen|page|surface|dashboard|home)\b/.test(value)) return 'screen';
  return 'table';
}

function widgetForIntent(intent: Exclude<PackageChangeIntent, 'table' | 'theme' | 'workflow'>): NonNullable<A2UiComponent['widget']> {
  if (intent === 'form') return 'formCard';
  if (intent === 'board') return 'kanbanBoard';
  if (intent === 'feed') return 'feedList';
  if (intent === 'poll') return 'pollCard';
  if (intent === 'calendar') return 'calendarBlock';
  if (intent === 'timeline') return 'timelineBlock';
  if (intent === 'gallery') return 'galleryGrid';
  if (intent === 'media') return 'mediaBlock';
  if (intent === 'link') return 'linkPreview';
  if (intent === 'map') return 'mapBlock';
  if (intent === 'chart') return 'chartBlock';
  return 'dataTable';
}

function viewModeForIntent(intent: PackageChangeIntent): 'list' | 'board' | 'table' | 'calendar' | 'timeline' | 'chart' {
  if (intent === 'board') return 'board';
  if (intent === 'calendar') return 'calendar';
  if (intent === 'timeline') return 'timeline';
  if (intent === 'screen' || intent === 'form') return 'table';
  if (intent === 'chart') return 'chart';
  return 'list';
}

function fieldsForIntent(intent: PackageChangeIntent) {
  const base = {
    id: { type: 'text' as const, required: true, indexed: true },
    title: { type: 'text' as const, required: true, indexed: true },
    status: { type: 'text' as const, indexed: true },
    body: { type: 'text' as const },
    updated_at: { type: 'timestamp' as const, indexed: true },
    properties: { type: 'json' as const },
  };
  if (intent === 'poll') return { ...base, options: { type: 'json' as const }, votes: { type: 'json' as const } };
  if (intent === 'calendar') return { ...base, starts_at: { type: 'timestamp' as const, indexed: true }, ends_at: { type: 'timestamp' as const } };
  if (intent === 'timeline') return { ...base, happened_at: { type: 'timestamp' as const, indexed: true } };
  if (intent === 'gallery' || intent === 'media') return { ...base, media: { type: 'json' as const }, url: { type: 'text' as const } };
  if (intent === 'link') return { ...base, url: { type: 'text' as const, indexed: true }, preview: { type: 'json' as const } };
  if (intent === 'map') return { ...base, location: { type: 'json' as const }, address: { type: 'text' as const } };
  if (intent === 'chart') return { ...base, value: { type: 'number' as const, indexed: true }, series: { type: 'json' as const } };
  if (intent === 'form') return { ...base, answers: { type: 'json' as const } };
  return base;
}

function componentsForIntent(
  name: PackageChangeName,
  intent: Exclude<PackageChangeIntent, 'table' | 'theme' | 'workflow'>,
  collectionIntent: PackageChangeIntent,
  widget: NonNullable<A2UiComponent['widget']>,
): A2UiComponent[] {
  const title = `${name.label} ${intent === 'screen' ? 'screen' : intent}`;
  const collectionQuery = { collections: [name.collectionId], limit: 12 };
  const widgetProps = propsForIntent(name, intent);
  return [
    {
      kind: 'widget',
      widget,
      id: `${name.screenId}_${intent}`,
      title: titleCase(title),
      subtitle: subtitleForIntent(intent),
      props: widgetProps,
      tone: toneForIntent(intent),
    },
    {
      kind: 'recordList',
      id: `${name.collectionId}_records`,
      title: `${name.label} records`,
      subtitle: `Stored in ${name.collectionId}; view mode ${viewModeForIntent(collectionIntent)}.`,
      query: collectionQuery,
    },
    {
      kind: 'widget',
      widget: 'dataTable',
      id: `${name.collectionId}_table`,
      title: `${name.label} table`,
      subtitle: 'The same data stays inspectable as structured rows.',
      props: { columns: Object.keys(fieldsForIntent(collectionIntent)).slice(0, 4).map((label) => ({ label })) },
      tone: 'blue',
    },
  ];
}

function propsForIntent(name: PackageChangeName, intent: Exclude<PackageChangeIntent, 'table' | 'theme' | 'workflow'>): Record<string, unknown> {
  if (intent === 'form') return { fields: [{ label: 'Title', subtitle: 'Short text' }, { label: 'Status', subtitle: 'Choice' }, { label: 'Notes', subtitle: 'Long text' }] };
  if (intent === 'board') return { columns: [{ title: 'Ideas', items: [{ title: `Plan ${name.label}` }] }, { title: 'Doing', items: [] }, { title: 'Done', items: [] }] };
  if (intent === 'poll') return { options: [{ label: 'Yes' }, { label: 'No' }, { label: 'Maybe' }] };
  if (intent === 'calendar') return { events: [{ title: name.label, subtitle: 'First scheduled item', when: 'Soon' }] };
  if (intent === 'timeline') return { items: [{ title: 'Created', subtitle: 'Added by package diff' }, { title: 'Next', subtitle: 'Add real events' }] };
  if (intent === 'gallery') return { items: [{ title: 'Photo', emoji: '◼︎' }, { title: 'Clip', emoji: '▶︎' }, { title: 'Doc', emoji: '◇' }] };
  if (intent === 'media') return { body: 'Drop video, audio, image, or link records into this package collection.' };
  if (intent === 'link') return { url: 'https://example.com', subtitle: 'Safe URL preview surface.' };
  if (intent === 'map') return { body: 'Render places, stores, routes, homes, or field work from package data.' };
  if (intent === 'chart') return { points: [{ label: 'A', value: 4 }, { label: 'B', value: 8 }, { label: 'C', value: 5 }] };
  if (intent === 'feed') return { items: [{ title: `${name.label} update`, subtitle: 'Feed item from package config' }] };
  return {};
}

function subtitleForIntent(intent: Exclude<PackageChangeIntent, 'table' | 'theme' | 'workflow'>) {
  if (intent === 'form') return 'Collect structured data without hand-built screens.';
  if (intent === 'board') return 'Kanban-quality grouped records from config.';
  if (intent === 'feed') return 'Posts, updates, links, comments, and activity.';
  if (intent === 'poll') return 'Voting and choice UI as a safe package primitive.';
  if (intent === 'calendar') return 'Schedules and events as package data.';
  if (intent === 'timeline') return 'History, provenance, and milestones.';
  if (intent === 'gallery') return 'Visual collections for images and assets.';
  if (intent === 'media') return 'Video, audio, image, and link surfaces.';
  if (intent === 'link') return 'Bookmarks, YouTube, recipes, docs, and source previews.';
  if (intent === 'map') return 'Places, stores, routes, homes, and field work.';
  if (intent === 'chart') return 'Numbers, trends, budgets, signals, and reports.';
  return 'A generated JSON-render screen.';
}

function toneForIntent(intent: Exclude<PackageChangeIntent, 'table' | 'theme' | 'workflow'>): A2UiComponent['tone'] {
  if (intent === 'board' || intent === 'calendar') return 'blue';
  if (intent === 'poll' || intent === 'timeline') return 'amber';
  if (intent === 'media' || intent === 'gallery') return 'plum';
  if (intent === 'link' || intent === 'map' || intent === 'chart') return 'blue';
  return 'moss';
}

function derivePackageChangeName(prompt: string) {
  const clean = prompt
    .replace(/\b(add|create|make|new|table|screen|surface|collection|with|for|a|an|the|and|or|theme|workflow|rule|when|suggest|automate|remind)\b/gi, ' ')
    .replace(/\b(form|input|survey|board|kanban|feed|post|posts|poll|vote|calendar|schedule|timeline|history|gallery|photo|photos|media|video|audio|youtube|link|url|preview|bookmark|map|location|chart|graph|analytics|dashboard|page|permission|permissions|capability|capabilities|intent|intents|native)\b/gi, ' ')
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

function mergeNativeCapabilityRequest(
  current: AppPackageNativeCapability,
  prompt: string,
): AppPackageNativeCapability {
  const lower = prompt.toLowerCase();
  const permissions = [...(current.permissions ?? [])];
  const intents = [...(current.intents ?? [])];

  if (/\b(camera|receipt|scan|photo)\b/.test(lower)) {
    upsertPermission(permissions, {
      id: 'camera-capture',
      platform: 'expo',
      permission: 'expo-image-picker:camera',
      reason: 'Capture receipts, labels, pantry photos, meal images, and visual evidence for package records.',
      required: false,
      prompt: 'Allow camera access when you capture food evidence.',
    });
  }
  if (/\b(photo library|photos?|gallery|album)\b/.test(lower)) {
    upsertPermission(permissions, {
      id: 'photo-library',
      platform: 'expo',
      permission: 'expo-image-picker:media-library',
      reason: 'Attach existing food photos, receipts, labels, and screenshots to package records.',
      required: false,
      prompt: 'Allow photo-library access when you attach food evidence.',
    });
  }
  if (/\b(health connect|nutrition|steps|sleep|body|exercise)\b/.test(lower)) {
    upsertPermission(permissions, {
      id: 'health-connect-read-nutrition',
      platform: 'android',
      permission: 'android.permission.health.READ_NUTRITION',
      reason: 'Use Health Connect nutrition records as optional food context after user approval.',
      required: false,
      prompt: 'Allow WonderFood to read nutrition records for food context.',
    });
  }
  if (/\b(share sheet|share intent|share|send to app)\b/.test(lower)) {
    upsertIntent(intents, {
      id: 'receive-shared-content',
      platform: 'expo',
      kind: 'share',
      reason: 'Receive links, recipes, photos, videos, notes, and files shared into the active package.',
      required: false,
    });
  }
  if (/\b(deep[- ]?link|url open|open url|link into app)\b/.test(lower)) {
    upsertIntent(intents, {
      id: 'open-package-link',
      platform: 'expo',
      kind: 'deep_link',
      reason: 'Open specific package screens, records, and actions from trusted links.',
      required: false,
    });
  }
  if (/\b(shortcut|launcher|quick action)\b/.test(lower)) {
    upsertIntent(intents, {
      id: 'package-shortcut',
      platform: 'android',
      kind: 'shortcut',
      reason: 'Expose common package actions as app shortcuts.',
      required: false,
    });
  }
  if (/\b(voice|okay google|google assistant|assistant)\b/.test(lower)) {
    upsertIntent(intents, {
      id: 'voice-command',
      platform: 'android',
      kind: 'voice',
      reason: 'Let supported assistants route approved package commands into WonderFood.',
      required: false,
    });
  }
  if (/\b(background|scheduled|periodic|sync|reminder)\b/.test(lower)) {
    upsertIntent(intents, {
      id: 'background-package-task',
      platform: 'expo',
      kind: 'background_task',
      reason: 'Run package-approved reminders, refreshes, and provider checks in the background where supported.',
      required: false,
    });
  }
  if (/\b(file open|open file|document|pdf|csv|import file)\b/.test(lower)) {
    upsertIntent(intents, {
      id: 'open-package-file',
      platform: 'expo',
      kind: 'file_open',
      reason: 'Open package-supported files such as PDFs, CSVs, images, audio, and video as records.',
      required: false,
    });
  }
  if (intents.length === (current.intents ?? []).length && permissions.length === (current.permissions ?? []).length) {
    upsertIntent(intents, {
      id: 'package-capability-request',
      platform: current.platform,
      kind: 'url_open',
      reason: 'Generic package capability request captured from an AI package-edit prompt.',
      required: false,
    });
  }

  return cleanJson({
    ...current,
    permissions: sortById(permissions),
    intents: sortById(intents),
  }) as AppPackageNativeCapability;
}

function upsertPermission(
  permissions: Array<string | AppPackagePermissionDeclaration>,
  next: AppPackagePermissionDeclaration,
) {
  const index = permissions.findIndex((permission) => {
    if (typeof permission === 'string') return permission === next.permission || permission === next.id;
    return permission.id === next.id || permission.permission === next.permission;
  });
  if (index >= 0) {
    permissions[index] = typeof permissions[index] === 'string' ? next : { ...(permissions[index] as AppPackagePermissionDeclaration), ...next };
    return;
  }
  permissions.push(next);
}

function upsertIntent(
  intents: NonNullable<AppPackageNativeCapability['intents']>,
  next: NonNullable<AppPackageNativeCapability['intents']>[number],
) {
  const index = intents.findIndex((intent) => intent.id === next.id || intent.kind === next.kind);
  if (index >= 0) {
    intents[index] = { ...intents[index], ...next };
    return;
  }
  intents.push(next);
}

function sortById<T>(items: T[]): T[] {
  return [...items].sort((left, right) => labelForSort(left).localeCompare(labelForSort(right)));
}

function labelForSort(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value && typeof value === 'object') {
    const record = value as { id?: unknown; permission?: unknown; kind?: unknown };
    return String(record.id ?? record.permission ?? record.kind ?? '');
  }
  return '';
}

function hashValue(value: unknown): string {
  return `sha256:${sha256(stableJson(value))}`;
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>)
      .filter((key) => (value as Record<string, unknown>)[key] !== undefined)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson((value as Record<string, unknown>)[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value) ?? 'null';
}

function cleanJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(cleanJson);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, child]) => child !== undefined)
        .map(([key, child]) => [key, cleanJson(child)]),
    );
  }
  return value;
}
