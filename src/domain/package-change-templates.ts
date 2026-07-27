import { sha256 } from 'js-sha256';

import type {
  A2UiComponent,
  AppPackage,
  AppPackageNativeCapability,
  AppPackagePermissionDeclaration,
  AppPackageV3,
  FieldType,
  PackagePresentationSpec,
} from '@/packages/shared/contracts/package';
import type { QueryPredicate } from '@/packages/shared/contracts/query';
import type { AppPackageChangeRequest } from '@/src/db/app-package-registry';

type PackageChangeName = ReturnType<typeof derivePackageChangeName>;
type PackageChangeIntent =
  | 'control'
  | 'edit'
  | 'field'
  | 'view'
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
type WidgetScreenIntent = Exclude<PackageChangeIntent, 'control' | 'edit' | 'field' | 'view' | 'table' | 'theme' | 'workflow' | 'native'>;
type UiScreenSpec = NonNullable<NonNullable<PackagePresentationSpec['ui']>['screens']>[string];

export function buildSafePackageChangeRequest(active: AppPackage, prompt: string): AppPackageChangeRequest {
  const intent = classifyPackageChangeIntent(prompt);
  const name = derivePackageChangeName(prompt);
  const presentation = active.presentation;
  if (!presentation) throw new Error('Active package has no presentation section.');

  if (intent === 'control') return buildControlRoomChange(active, presentation);
  if (intent === 'edit') return buildScreenEditChange(active, presentation, prompt);
  if (intent === 'field') return buildFieldChange(active, presentation, prompt);
  if (intent === 'view') return buildQueryViewChange(active, presentation, name, prompt);
  if (intent === 'theme') return buildThemeChange(active, presentation, name);
  if (intent === 'workflow') return buildWorkflowChange(active, presentation, name);
  if (intent === 'native') return buildNativeCapabilityChange(active, presentation, name, prompt);
  if (intent !== 'table') return buildWidgetScreenChange(active, presentation, name, intent);
  return buildTableScreenChange(active, presentation, name);
}

function buildControlRoomChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
): AppPackageChangeRequest {
  const collections = selectControlRoomCollections(active);
  const screenName = {
    label: 'Control Room',
    collectionId: collections[0] ?? Object.keys(active.collections).sort()[0] ?? 'records',
    screenId: 'ai_control_room',
    surfaceId: 'ai_control_room',
  };
  if (presentation.surfaces.some((surface) => surface.id === screenName.surfaceId)) {
    throw new Error(`Surface already exists: ${screenName.surfaceId}`);
  }
  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      {
        op: 'add',
        path: '/presentation/surfaces/-',
        value: {
          id: screenName.surfaceId,
          label: screenName.label,
          collections,
        },
      },
      ...buildUiScreenPatches(presentation, screenName, [
        {
          kind: 'widget',
          widget: 'assistantChat',
          id: 'control_ai_editor',
          title: 'Ask Wonder to change this app',
          subtitle: 'Add fields, tune screens, create views, change workflows, or request native capabilities.',
          tone: 'plum',
          props: {
            mode: 'packageEditor',
            safeDiffsOnly: true,
            approvalRequired: true,
          },
        },
        {
          kind: 'widget',
          widget: 'providerStatus',
          id: 'control_provider_status',
          title: 'Connected sources',
          subtitle: 'Notion, Sheets, Drive, and local records stay behind provider verification.',
          tone: 'blue',
          props: {
            providers: ['local', 'notion', 'sheets', 'drive'],
          },
        },
        {
          kind: 'action',
          id: 'control_connect_source',
          title: 'Connect a source',
          subtitle: 'Pick Notion, Sheets, or Drive; Wonder creates the provider binding and keeps writes approval-backed.',
          tone: 'blue',
          action: {
            kind: 'propose',
            label: 'Connect',
            command: 'open_provider_connection_flow',
            payload: { providers: ['notion', 'google_sheets', 'google_drive'] },
          },
        },
        {
          kind: 'action',
          id: 'control_verify_sync',
          title: 'Verify sync',
          subtitle: 'Run a safe reread check before trusting any provider writeback.',
          tone: 'moss',
          action: {
            kind: 'propose',
            label: 'Verify',
            command: 'run_provider_verification',
            payload: { providers: ['notion', 'google_sheets', 'google_drive'] },
          },
        },
        {
          kind: 'widget',
          widget: 'widgetCatalog',
          id: 'control_widget_catalog',
          title: 'Building blocks',
          subtitle: 'Supported JSON-render widgets for generated apps.',
          tone: 'moss',
          props: {
            widgets: ['postCard', 'pollCard', 'linkPreview', 'feedList', 'kanbanBoard', 'chartBlock', 'mediaBlock', 'mapBlock', 'formCard', 'calendarBlock', 'timelineBlock', 'galleryGrid', 'dataTable'],
          },
        },
        {
          kind: 'widget',
          widget: 'schemaEditor',
          id: 'control_schema_editor',
          title: 'Data model',
          subtitle: 'Schema changes are package diffs with approval receipts.',
          tone: 'amber',
          props: {
            collections,
            editable: true,
          },
        },
        {
          kind: 'widget',
          widget: 'permissionCard',
          id: 'control_permissions',
          title: 'Native capabilities',
          subtitle: 'Camera, share, shortcuts, files, voice, and Health Connect are declared before use.',
          tone: 'amber',
          props: active.schemaVersion === 'wonder.app-package.v3'
            ? active.nativeCapabilities
            : { permissions: [], intents: [] },
        },
        {
          kind: 'widget',
          widget: 'themePreview',
          id: 'control_theme',
          title: 'Theme',
          subtitle: 'Theme tokens live in package config.',
          tone: 'moss',
          props: presentation.visualIdentity ?? {},
        },
      ]),
    ],
  };
}

function buildScreenEditChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  prompt: string,
): AppPackageChangeRequest {
  const target = deriveScreenEditChange(presentation, prompt);
  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      {
        op: 'replace',
        path: `/presentation/ui/screens/${escapeJsonPointer(target.screenId)}`,
        value: target.screen,
      },
    ],
  };
}

function buildQueryViewChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  name: PackageChangeName,
  prompt: string,
): AppPackageChangeRequest {
  const target = deriveQueryViewChange(active, name, prompt);
  if (active.queries[target.queryId]) throw new Error(`Query already exists: ${target.queryId}`);
  if (active.views[target.viewId]) throw new Error(`View already exists: ${target.viewId}`);
  if (presentation.ui?.screens?.[target.screenId]) throw new Error(`Screen already exists: ${target.screenId}`);

  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      {
        op: 'add',
        path: `/queries/${escapeJsonPointer(target.queryId)}`,
        value: {
          from: 'records',
          where: target.where,
          orderBy: [{ field: 'updated_at', direction: 'desc' }],
          limit: 24,
        },
      },
      {
        op: 'add',
        path: `/views/${escapeJsonPointer(target.viewId)}`,
        value: {
          id: target.viewId,
          query: target.queryId,
          mode: target.mode,
          fields: target.fields,
          ...(target.groupBy ? { groupBy: target.groupBy } : {}),
        },
      },
      {
        op: 'add',
        path: '/presentation/surfaces/-',
        value: {
          id: target.surfaceId,
          label: target.label,
          collections: [target.collectionId],
          views: [target.viewId],
        },
      },
      ...buildUiScreenPatches(presentation, {
        label: target.label,
        collectionId: target.collectionId,
        screenId: target.screenId,
        surfaceId: target.surfaceId,
      }, [
        {
          kind: 'widget',
          widget: widgetForViewMode(target.mode),
          id: `${target.screenId}_${target.mode}`,
          title: target.label,
          subtitle: `Filtered ${target.collectionLabel} view powered by AppPackage query ${target.queryId}.`,
          view: target.viewId,
          props: propsForViewMode(target.mode, target.label),
          tone: toneForViewMode(target.mode),
        },
        {
          kind: 'recordList',
          id: `${target.screenId}_records`,
          title: `${target.collectionLabel} records`,
          subtitle: 'Same source data; different package-config view.',
          query: { collections: [target.collectionId], limit: 12 },
        },
      ]),
    ],
  };
}

function buildFieldChange(
  active: AppPackage,
  presentation: PackagePresentationSpec,
  prompt: string,
): AppPackageChangeRequest {
  const target = deriveFieldChange(active, prompt);
  if (active.collections[target.collectionId].fields[target.fieldId]) {
    throw new Error(`Field already exists: ${target.collectionId}.${target.fieldId}`);
  }
  const queryEntries = Object.entries(active.queries);
  const affectedViewIds = Object.entries(active.views)
    .filter(([, view]) => {
      const query = active.queries[view.query];
      return Boolean(query && queryTargetsCollection(query, target.collectionId) && !view.fields.includes(target.fieldId));
    })
    .map(([viewId]) => viewId);

  return {
    basePackageKey: `${active.id}@${active.version}`,
    requestedBy: 'mobile-package-editor',
    patch: [
      versionPatch(active.version),
      {
        op: 'add',
        path: `/collections/${escapeJsonPointer(target.collectionId)}/fields/${escapeJsonPointer(target.fieldId)}`,
        value: {
          type: target.fieldType,
          ...(target.indexed ? { indexed: true } : {}),
        },
      },
      ...affectedViewIds.map((viewId) => ({
        op: 'add' as const,
        path: `/views/${escapeJsonPointer(viewId)}/fields/-`,
        value: target.fieldId,
      })),
      ...buildUiScreenPatches(presentation, {
        label: `${target.collectionLabel} Schema`,
        collectionId: target.collectionId,
        screenId: `ai_${target.collectionId}_${target.fieldId}_schema`,
        surfaceId: `ai_${target.collectionId}_${target.fieldId}_schema`,
      }, [
        {
          kind: 'widget',
          widget: 'schemaEditor',
          id: `${target.collectionId}_${target.fieldId}_schema_editor`,
          title: `${target.fieldLabel} field`,
          subtitle: `Added ${target.fieldType} field to ${target.collectionLabel}.`,
          props: {
            collection: target.collectionId,
            field: target.fieldId,
            type: target.fieldType,
            affectedViews: affectedViewIds,
            matchingQueries: queryEntries
              .filter(([, query]) => queryTargetsCollection(query, target.collectionId))
              .map(([queryId]) => queryId),
          },
          tone: 'blue',
        },
        {
          kind: 'widget',
          widget: 'dataTable',
          id: `${target.collectionId}_${target.fieldId}_data_preview`,
          title: `${target.collectionLabel} fields`,
          subtitle: 'Schema change is package-config only and reviewable before activation.',
          props: {
            columns: Object.keys({
              ...active.collections[target.collectionId].fields,
              [target.fieldId]: { type: target.fieldType },
            }).map((label) => ({ label })),
          },
          tone: 'moss',
        },
      ]),
    ],
  };
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
  intent: WidgetScreenIntent,
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
          kind: 'action',
          id: `${name.screenId}_open_permissions`,
          title: 'Open permission setup',
          subtitle: 'Route users to the native permission request/status flow for this package.',
          tone: 'blue',
          action: {
            kind: 'propose',
            label: 'Open setup',
            command: 'open_native_permission_setup',
            payload: { surface: `${name.screenId}_permissions` },
          },
        },
        {
          kind: 'action',
          id: `${name.screenId}_test_intents`,
          title: 'Test app intents',
          subtitle: 'Verify share, shortcut, deep-link, voice, background, and file-open hooks before relying on them.',
          tone: 'moss',
          action: {
            kind: 'propose',
            label: 'Test intents',
            command: 'test_native_intents',
            payload: { surface: `${name.screenId}_permissions` },
          },
        },
        {
          kind: 'text',
          id: `${name.screenId}_permission_note`,
          title: 'Permission contract',
          subtitle: 'Package config declares what the shell may ask for; the user still grants OS permission in the native flow.',
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
  if (/\b(settings?|control room|config room|package editor|app editor|control center|admin|setup)\b/.test(value)) return 'control';
  if (/\b(theme|color|style|visual|design|cute|density|card|cards)\b/.test(value)) return 'theme';
  if (/\b(rule|workflow|when|expires|expire|automate|suggest|remind)\b/.test(value)) return 'workflow';
  if (/\b(permission|permissions|capability|capabilities|camera|photo library|photos?|voice|okay google|google assistant|shortcut|deep[- ]?link|background|file open|open file|health connect|share sheet|share intent)\b/.test(value)) return 'native';
  if (/\b(edit|update|rename|change|rewrite|revise|move|reorder|bring|promote|shorten|shorter|smaller|compact|less dense|simplify|polish|clean up|tune|tighten)\b/.test(value)) return 'edit';
  if (/\bfield\b/.test(value) && !/\b(form|survey)\b/.test(value)) return 'field';
  if (/\b(view|views|show|filter|filtered|list of|board of|calendar of|timeline of|chart of|dashboard for|report for)\b/.test(value)) return 'view';
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

function widgetForIntent(intent: WidgetScreenIntent): NonNullable<A2UiComponent['widget']> {
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
  intent: WidgetScreenIntent,
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

function propsForIntent(name: PackageChangeName, intent: WidgetScreenIntent): Record<string, unknown> {
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

function subtitleForIntent(intent: WidgetScreenIntent) {
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

function toneForIntent(intent: WidgetScreenIntent): A2UiComponent['tone'] {
  if (intent === 'board' || intent === 'calendar') return 'blue';
  if (intent === 'poll' || intent === 'timeline') return 'amber';
  if (intent === 'media' || intent === 'gallery') return 'plum';
  if (intent === 'link' || intent === 'map' || intent === 'chart') return 'blue';
  return 'moss';
}

function derivePackageChangeName(prompt: string) {
  const clean = prompt
    .replace(/\b(add|create|make|new|show|view|views|filter|filtered|list|report|table|screen|surface|collection|with|for|a|an|the|and|or|theme|workflow|rule|when|suggest|automate|remind)\b/gi, ' ')
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

type FieldChangeTarget = {
  collectionId: string;
  collectionLabel: string;
  fieldId: string;
  fieldLabel: string;
  fieldType: FieldType;
  indexed: boolean;
};

type ScreenEditChangeTarget = {
  screenId: string;
  screen: UiScreenSpec;
};

function deriveScreenEditChange(
  presentation: PackagePresentationSpec,
  prompt: string,
): ScreenEditChangeTarget {
  const screens = presentation.ui?.screens;
  if (!screens || Object.keys(screens).length === 0) {
    throw new Error('No JSON-render screens available to edit.');
  }
  const lower = prompt.toLowerCase();
  const screenId = findTargetScreenId(screens, lower, presentation.ui?.defaultScreen);
  const existing = screens[screenId];
  const compact = /\b(shorten|shorter|smaller|compact|less dense|simplify|tighten)\b/.test(lower);
  const componentIndex = existing.components ? findTargetComponentIndex(existing.components, lower) : -1;
  const editsComponent = componentIndex >= 0;
  const title = editsComponent ? existing.title : deriveEditedScreenTitle(existing.title, screenId, prompt);
  const subtitle = editsComponent ? existing.subtitle : deriveEditedScreenSubtitle(existing.subtitle, compact, lower);
  const components = existing.components
    ? tuneScreenComponents(existing.components, lower, prompt, compact, componentIndex)
    : undefined;
  return {
    screenId,
    screen: cleanJson({
      ...existing,
      title,
      subtitle,
      ...(components ? { components } : {}),
    }) as UiScreenSpec,
  };
}

type QueryViewChangeTarget = {
  collectionId: string;
  collectionLabel: string;
  queryId: string;
  viewId: string;
  screenId: string;
  surfaceId: string;
  label: string;
  where: QueryPredicate;
  mode: 'list' | 'board' | 'table' | 'calendar' | 'timeline' | 'chart';
  fields: string[];
  groupBy?: string;
};

function deriveQueryViewChange(
  active: AppPackage,
  name: PackageChangeName,
  prompt: string,
): QueryViewChangeTarget {
  const lower = prompt.toLowerCase();
  const collectionId = findTargetCollection(active, lower);
  const collection = active.collections[collectionId];
  const collectionLabel = titleCase(collectionId.replace(/[_:-]+/g, ' '));
  const mode = viewModeForViewPrompt(lower);
  const slug = name.screenId.replace(/^ai_/, '') || collectionId;
  const baseId = `ai_${collectionId}_${slug}_${mode}`.replace(/_+/g, '_');
  const fields = selectViewFields(collection.fields, mode);
  const where: QueryPredicate = { op: 'eq', field: 'collection', value: collectionId };
  const groupBy = mode === 'board'
    ? preferredExistingField(collection.fields, ['status', 'state', 'stage', 'priority'])
    : undefined;
  const label = `${name.label === 'Notes' ? collectionLabel : name.label} ${titleCase(mode)}`;
  return {
    collectionId,
    collectionLabel,
    queryId: `${baseId}_query`,
    viewId: `${baseId}_view`,
    screenId: `${baseId}_screen`,
    surfaceId: `${baseId}_surface`,
    label,
    where,
    mode,
    fields,
    ...(groupBy ? { groupBy } : {}),
  };
}

function deriveFieldChange(active: AppPackage, prompt: string): FieldChangeTarget {
  const lower = prompt.toLowerCase();
  const collectionId = findTargetCollection(active, lower);
  const collectionLabel = titleCase(collectionId.replace(/[_:-]+/g, ' '));
  const collectionWords = new Set(collectionId.toLowerCase().split(/[_:-]+/).filter(Boolean));
  const clean = prompt
    .replace(/\b(add|create|make|new|field|column|property|attribute|to|in|on|for|a|an|the|and|or|with|into)\b/gi, ' ')
    .replace(new RegExp(`\\b(${[...collectionWords].map(escapeRegExp).join('|')})\\b`, 'gi'), ' ')
    .replace(/\b(text|number|numeric|amount|price|cost|count|quantity|score|rating|date|time|timestamp|due|expires|boolean|checkbox|yes|no|flag|json|object|tags|list|options)\b/gi, ' ')
    .replace(/[^a-z0-9 ]/gi, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 3)
    .join(' ');
  const fieldLabel = titleCase(clean || 'Notes');
  const fieldId = (clean || 'notes').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'notes';
  const fieldType = inferFieldType(lower);
  return {
    collectionId,
    collectionLabel,
    fieldId,
    fieldLabel,
    fieldType,
    indexed: fieldType !== 'json',
  };
}

function findTargetCollection(active: AppPackage, lowerPrompt: string): string {
  const entries = Object.keys(active.collections);
  const direct = entries.find((id) => lowerPrompt.includes(id.toLowerCase()));
  if (direct) return direct;
  const byWords = entries.find((id) => id.toLowerCase().split(/[_:-]+/).some((word) => word.length > 2 && lowerPrompt.includes(word)));
  if (byWords) return byWords;
  if (Object.hasOwn(active.collections, 'inventory')) return 'inventory';
  if (Object.hasOwn(active.collections, 'records')) return 'records';
  const first = entries.sort()[0];
  if (!first) throw new Error('No collections available for field change.');
  return first;
}

function selectControlRoomCollections(active: AppPackage): string[] {
  const preferred = ['inventory', 'recipe', 'meal_plan', 'shopping_item', 'provider_connection'];
  const selected = preferred.filter((collection) => Object.hasOwn(active.collections, collection));
  for (const collection of Object.keys(active.collections).sort()) {
    if (selected.length >= 5) break;
    if (!selected.includes(collection)) selected.push(collection);
  }
  if (selected.length === 0) {
    throw new Error('No collections available for control room.');
  }
  return selected;
}

function inferFieldType(lowerPrompt: string): FieldType {
  if (/\b(number|numeric|amount|price|cost|count|quantity|score|rating|calorie|calories|grams?|servings?)\b/.test(lowerPrompt)) return 'number';
  if (/\b(date|time|timestamp|due|expires|expiry|scheduled|starts?|ends?)\b/.test(lowerPrompt)) return 'timestamp';
  if (/\b(boolean|checkbox|yes\/no|yes no|flag|done|enabled)\b/.test(lowerPrompt)) return 'boolean';
  if (/\b(json|object|tags|list|options|array|metadata)\b/.test(lowerPrompt)) return 'json';
  return 'text';
}

function viewModeForViewPrompt(lowerPrompt: string): QueryViewChangeTarget['mode'] {
  if (/\b(board|kanban|pipeline|columns?)\b/.test(lowerPrompt)) return 'board';
  if (/\b(calendar|schedule|events?|agenda)\b/.test(lowerPrompt)) return 'calendar';
  if (/\b(timeline|history|milestone|journey|log)\b/.test(lowerPrompt)) return 'timeline';
  if (/\b(chart|graph|analytics|trend|report)\b/.test(lowerPrompt)) return 'chart';
  if (/\b(table|spreadsheet|grid)\b/.test(lowerPrompt)) return 'table';
  return 'list';
}

function widgetForViewMode(mode: QueryViewChangeTarget['mode']): NonNullable<A2UiComponent['widget']> {
  if (mode === 'board') return 'kanbanBoard';
  if (mode === 'calendar') return 'calendarBlock';
  if (mode === 'timeline') return 'timelineBlock';
  if (mode === 'chart') return 'chartBlock';
  if (mode === 'table') return 'dataTable';
  return 'feedList';
}

function toneForViewMode(mode: QueryViewChangeTarget['mode']): A2UiComponent['tone'] {
  if (mode === 'board' || mode === 'calendar' || mode === 'chart') return 'blue';
  if (mode === 'timeline') return 'amber';
  return 'moss';
}

function propsForViewMode(mode: QueryViewChangeTarget['mode'], label: string): Record<string, unknown> {
  if (mode === 'board') return { columns: [{ title: 'Open', items: [{ title: label }] }, { title: 'Next', items: [] }, { title: 'Done', items: [] }] };
  if (mode === 'calendar') return { events: [{ title: label, when: 'From package query' }] };
  if (mode === 'timeline') return { items: [{ title: label, subtitle: 'Filtered package view' }] };
  if (mode === 'chart') return { points: [{ label: 'Now', value: 1 }] };
  if (mode === 'table') return { columns: [{ label: 'title' }, { label: 'updated_at' }] };
  return { items: [{ title: label, subtitle: 'Filtered package view' }] };
}

function selectViewFields(
  fields: AppPackage['collections'][string]['fields'],
  mode: QueryViewChangeTarget['mode'],
): string[] {
  const preferred = mode === 'chart'
    ? ['title', 'value', 'score', 'amount', 'count', 'updated_at']
    : mode === 'calendar'
      ? ['title', 'starts_at', 'ends_at', 'updated_at']
      : mode === 'timeline'
        ? ['title', 'happened_at', 'updated_at']
        : ['title', 'status', 'state', 'stage', 'body', 'updated_at'];
  const selected = preferred.filter((field) => Object.hasOwn(fields, field));
  for (const field of Object.keys(fields)) {
    if (selected.length >= 6) break;
    if (!selected.includes(field)) selected.push(field);
  }
  return selected.length ? selected : ['title'];
}

function preferredExistingField(
  fields: AppPackage['collections'][string]['fields'],
  preferred: string[],
): string | undefined {
  return preferred.find((field) => Object.hasOwn(fields, field));
}

function findTargetScreenId(
  screens: Record<string, UiScreenSpec>,
  lowerPrompt: string,
  defaultScreen?: string,
): string {
  const entries = Object.entries(screens);
  const byId = entries.find(([screenId]) => lowerPrompt.includes(screenId.toLowerCase()));
  if (byId) return byId[0];
  const byTitle = entries.find(([, screen]) => (
    typeof screen.title === 'string'
      && screen.title.trim().length > 0
      && lowerPrompt.includes(screen.title.toLowerCase())
  ));
  if (byTitle) return byTitle[0];
  if (defaultScreen && screens[defaultScreen]) return defaultScreen;
  const first = entries[0]?.[0];
  if (!first) throw new Error('No JSON-render screens available to edit.');
  return first;
}

function deriveEditedScreenTitle(current: unknown, screenId: string, prompt: string): string {
  const quoted = prompt.match(/["“]([^"”]{2,48})["”]/)?.[1]?.trim();
  if (quoted) return quoted;
  return typeof current === 'string' && current.trim()
    ? current
    : titleCase(screenId.replace(/[_:-]+/g, ' '));
}

function deriveEditedScreenSubtitle(current: unknown, compact: boolean, lowerPrompt: string): string {
  if (compact) {
    return 'Compact by default: best next action first, details nested behind records and actions.';
  }
  if (/\b(polish|clean up|tune)\b/.test(lowerPrompt)) {
    return 'Polished by AI through a reviewable package diff; core data and actions stay unchanged.';
  }
  return typeof current === 'string' && current.trim()
    ? current
    : 'AI-edited JSON-render screen.';
}

function tuneScreenComponents(
  components: A2UiComponent[],
  lowerPrompt: string,
  prompt: string,
  compact: boolean,
  targetIndex: number,
): A2UiComponent[] {
  const tuned = components.map((component, index) => (
    targetIndex === -1 || targetIndex === index
      ? tuneScreenComponent(component, index, lowerPrompt, prompt, compact, targetIndex === index)
      : component
  ));
  if (targetIndex >= 0 && shouldMoveComponentFirst(lowerPrompt)) {
    const [target] = tuned.splice(targetIndex, 1);
    if (target) tuned.unshift(target);
  }
  return compact && targetIndex === -1 ? tuned.slice(0, Math.min(5, tuned.length)) : tuned;
}

function tuneScreenComponent(
  component: A2UiComponent,
  index: number,
  lowerPrompt: string,
  prompt: string,
  compact: boolean,
  targetMatched: boolean,
): A2UiComponent {
  const renamedTitle = targetMatched ? quotedPromptValue(prompt) : undefined;
  const tuned: A2UiComponent = {
    ...component,
    ...(renamedTitle ? { title: renamedTitle } : {}),
    subtitle: trimComponentSubtitle(component.subtitle, compact),
  };
  if (compact && tuned.query?.limit) {
    tuned.query = { ...tuned.query, limit: Math.min(tuned.query.limit, index === 0 ? 3 : 2) };
  }
  if (compact && tuned.kind === 'widget') {
    tuned.props = {
      ...(tuned.props ?? {}),
      density: 'compact',
      summaryFirst: true,
    };
  }
  if (/\b(cute|warm|friendly)\b/.test(lowerPrompt) && tuned.tone === undefined) {
    tuned.tone = 'moss';
  }
  return tuned;
}

function findTargetComponentIndex(components: A2UiComponent[], lowerPrompt: string): number {
  return components.findIndex((component) => {
    const ids = [
      component.id,
      component.id?.replace(/[_:-]+/g, ' '),
      component.title,
      component.widget,
    ].filter((value): value is string => typeof value === 'string' && value.trim().length > 0);
    return ids.some((value) => lowerPrompt.includes(value.toLowerCase()));
  });
}

function shouldMoveComponentFirst(lowerPrompt: string): boolean {
  return /\b(first|top|above|front|start)\b/.test(lowerPrompt);
}

function quotedPromptValue(prompt: string): string | undefined {
  return prompt.match(/["“]([^"”]{2,48})["”]/)?.[1]?.trim();
}

function trimComponentSubtitle(subtitle: unknown, compact: boolean): string | undefined {
  if (typeof subtitle !== 'string' || !subtitle.trim()) return undefined;
  if (!compact) return subtitle;
  const clean = subtitle.trim();
  return clean.length <= 72 ? clean : `${clean.slice(0, 69).trimEnd()}…`;
}

function queryTargetsCollection(
  query: AppPackage['queries'][string],
  collectionId: string,
): boolean {
  return query.from === collectionId || predicateTargetsCollection(query.where, collectionId);
}

function predicateTargetsCollection(predicate: AppPackage['queries'][string]['where'], collectionId: string): boolean {
  if (!predicate) return false;
  if (predicate.op === 'eq' && predicate.field === 'collection' && predicate.value === collectionId) return true;
  if ((predicate.op === 'and' || predicate.op === 'or')) return predicate.args.some((arg) => predicateTargetsCollection(arg, collectionId));
  if (predicate.op === 'not') return predicateTargetsCollection(predicate.arg, collectionId);
  return false;
}

function escapeJsonPointer(value: string): string {
  return value.replace(/~/g, '~0').replace(/\//g, '~1');
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
