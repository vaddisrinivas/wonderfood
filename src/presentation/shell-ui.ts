import type { PackagePresentationUi } from '@/packages/shared/contracts/package';

export const HOME_SHELL_UI: PackagePresentationUi = {
  schemaVersion: 'wonder.ui.v1',
  defaultScreen: 'home',
  screens: {
    home: {
      title: 'Today',
      subtitle: 'One calm food command center. Dinner, pantry, shopping, and AI help stay in one place.',
      components: [
        {
          kind: 'recordList',
          id: 'home_tonight',
          title: 'Tonight',
          subtitle: 'Best next meal from your plans and recipes.',
          tone: 'moss',
          query: { collections: ['meal_plan', 'recipe'], match: 'planned|tonight|dinner', limit: 2 },
          action: { kind: 'propose', label: 'Ask Wonder', command: 'ask_today', payload: { route: '/chat' } },
        },
        {
          kind: 'recordList',
          id: 'home_use_first',
          title: 'Use first',
          subtitle: 'Food that deserves attention before it becomes waste.',
          tone: 'amber',
          query: { collections: ['inventory', 'inventory_lot', 'ingredient'], match: 'use soon|expire|best by|open', limit: 3 },
          action: { kind: 'propose', label: 'Plan around it', command: 'plan_around_expiry', payload: { route: '/chat' } },
        },
        {
          kind: 'recordList',
          id: 'home_shopping',
          title: 'Still needed',
          subtitle: 'Small, useful shopping signal — not a wall of data.',
          tone: 'blue',
          query: { collections: ['shopping_item', 'shopping_demand', 'shopping_list'], match: 'to buy|needed|shopping|cart', limit: 3 },
          action: { kind: 'propose', label: 'Add item', command: 'add_shopping_item', payload: { route: '/capture' } },
        },
        {
          kind: 'action',
          id: 'home_food',
          title: 'Open food workspace',
          subtitle: 'Meals, pantry, shopping, and review queue.',
          tone: 'moss',
          action: { kind: 'propose', label: 'Food', command: 'open_food', payload: { route: '/food' } },
        },
        {
          kind: 'action',
          id: 'home_chat',
          title: 'Ask Wonder',
          subtitle: 'Change data, ask questions, or redesign the app through AI proposals.',
          tone: 'plum',
          action: { kind: 'propose', label: 'Chat', command: 'open_chat', payload: { route: '/chat' } },
        },
        {
          kind: 'action',
          id: 'home_settings',
          title: 'Settings',
          subtitle: 'Sources, packages, Health Connect, theme, and debug controls.',
          tone: 'blue',
          action: { kind: 'propose', label: 'Settings', command: 'open_settings', payload: { route: '/settings' } },
        },
      ],
    },
  },
};

export const SETTINGS_SHELL_UI: PackagePresentationUi = {
  schemaVersion: 'wonder.ui.v1',
  defaultScreen: 'settings',
  screens: {
    settings: {
      title: 'Settings',
      subtitle: 'Quiet defaults first. Advanced control is one tap away when you want to change packages, sources, or app behavior.',
      components: [
        {
          kind: 'text',
          id: 'settings_best_defaults',
          title: 'Best defaults',
          subtitle: 'Wonder keeps food local-first, AI-assisted, and source-aware without putting debug controls on every page.',
          tone: 'moss',
        },
        {
          kind: 'action',
          id: 'settings_sources',
          title: 'Food sources',
          subtitle: 'Connect or review Notion, Sheets, and local records.',
          tone: 'blue',
          action: { kind: 'propose', label: 'Open sources', command: 'open_sources', payload: { route: '/sources' } },
        },
        {
          kind: 'action',
          id: 'settings_packages',
          title: 'Packages and app surfaces',
          subtitle: 'Change the active package, generated screens, density, and theme.',
          tone: 'plum',
          action: { kind: 'propose', label: 'Open package config', command: 'open_config', payload: { route: '/config' } },
        },
        {
          kind: 'action',
          id: 'settings_health',
          title: 'Health Connect',
          subtitle: 'Review Android Health Connect availability, permissions, and food-health signals.',
          tone: 'amber',
          action: { kind: 'propose', label: 'Open diagnostics', command: 'open_health', payload: { route: '/health-diagnostics' } },
        },
        {
          kind: 'action',
          id: 'settings_food',
          title: 'Back to food',
          subtitle: 'Return to the generated food workspace.',
          tone: 'moss',
          action: { kind: 'propose', label: 'Food', command: 'open_food', payload: { route: '/food' } },
        },
        {
          kind: 'action',
          id: 'settings_chat',
          title: 'Ask Wonder to change anything',
          subtitle: 'Add a table, change UI, rename sections, update defaults, or propose a package diff.',
          tone: 'plum',
          action: { kind: 'propose', label: 'Ask Wonder', command: 'open_chat', payload: { route: '/chat' } },
        },
      ],
    },
  },
};

export const ROUTE_SHELL_UI: PackagePresentationUi = {
  schemaVersion: 'wonder.ui.v1',
  defaultScreen: 'home',
  screens: {
    ...HOME_SHELL_UI.screens,
    ...SETTINGS_SHELL_UI.screens,
    chat: {
      title: 'Ask Wonder',
      subtitle: 'Ask, plan, and change the app from one AI surface. Advanced receipts stay behind the curtain.',
      components: [
        { kind: 'widget', widget: 'assistantChat', id: 'chat_assistant', title: 'Ask Wonder', subtitle: 'A real food assistant inside the JSON-render surface.', tone: 'plum', props: { prompt: 'Ask about dinner, pantry, shopping, or app changes…' } },
        { kind: 'recordList', id: 'chat_context', title: 'Context it can use', subtitle: 'Food records available for grounded answers.', tone: 'blue', query: { limit: 4 } },
      ],
    },
    sources: {
      title: 'Sources',
      subtitle: 'Local, Notion, and Sheets stay invisible until you need control.',
      components: [
        { kind: 'text', id: 'sources_default', title: 'Local first', subtitle: 'The app works on-device. External homes are optional and verified before writeback.', tone: 'moss' },
        { kind: 'action', id: 'sources_connect', title: 'Connect or verify', subtitle: 'Open provider setup only when needed.', tone: 'blue', action: { kind: 'propose', label: 'Open settings', command: 'open_settings', payload: { route: '/settings' } } },
        { kind: 'action', id: 'sources_food', title: 'Back to food', subtitle: 'Return to the main food workspace.', tone: 'moss', action: { kind: 'propose', label: 'Food', command: 'open_food', payload: { route: '/food' } } },
      ],
    },
    capture: {
      title: 'Add food',
      subtitle: 'Capture now. Wonder can organize it later.',
      components: [
        { kind: 'text', id: 'capture_note', title: 'Quick add', subtitle: 'Add a meal, pantry item, recipe, shopping need, or note by asking Wonder.', tone: 'amber' },
        { kind: 'action', id: 'capture_ask', title: 'Tell Wonder what to add', tone: 'moss', action: { kind: 'propose', label: 'Add with AI', command: 'capture_with_ai', payload: { route: '/chat' } } },
      ],
    },
    search: {
      title: 'Search',
      subtitle: 'Find food records first; ask Wonder when search is not enough.',
      components: [
        { kind: 'recordList', id: 'search_records', title: 'Recent food records', tone: 'blue', query: { limit: 6 } },
        { kind: 'action', id: 'search_ask', title: 'Ask instead', tone: 'plum', action: { kind: 'propose', label: 'Ask Wonder', command: 'ask_search', payload: { route: '/chat' } } },
      ],
    },
    record: {
      title: 'Record',
      subtitle: 'A source-backed item from your food graph.',
      components: [
        { kind: 'recordList', id: 'record_current', title: 'Selected item', tone: 'moss', query: { limit: 1 } },
        { kind: 'action', id: 'record_ask', title: 'Work with this item', tone: 'plum', action: { kind: 'propose', label: 'Ask Wonder', command: 'ask_record', payload: { route: '/chat' } } },
      ],
    },
    collection: {
      title: 'Collection',
      subtitle: 'A package-defined table rendered from config.',
      components: [
        { kind: 'recordList', id: 'collection_records', title: 'Records', tone: 'blue', query: { limit: 8 } },
        { kind: 'action', id: 'collection_edit', title: 'Change this table', tone: 'plum', action: { kind: 'propose', label: 'Ask Wonder', command: 'edit_collection', payload: { route: '/chat' } } },
      ],
    },
    config: {
      title: 'Customize',
      subtitle: 'Ask AI to change tables, package config, screens, theme, and defaults safely.',
      components: [
        { kind: 'widget', widget: 'schemaEditor', id: 'config_ai_first', title: 'AI edits. You approve.', subtitle: 'Describe the app change you want. Wonder should propose a package diff, not make hidden edits.', tone: 'plum' },
        { kind: 'widget', widget: 'widgetCatalog', id: 'config_widgets', title: 'Available building blocks', subtitle: 'What config can place on generated screens today.', tone: 'blue' },
        { kind: 'action', id: 'config_ask', title: 'Change the app', tone: 'moss', action: { kind: 'propose', label: 'Ask Wonder', command: 'change_app_config', payload: { route: '/chat' } } },
        { kind: 'action', id: 'config_settings', title: 'Settings', tone: 'blue', action: { kind: 'propose', label: 'Settings', command: 'open_settings', payload: { route: '/settings' } } },
      ],
    },
    system: {
      title: 'System',
      subtitle: 'Hidden machinery. Open only for troubleshooting.',
      components: [
        { kind: 'text', id: 'system_hidden', title: 'Behind the curtain', subtitle: 'Provider receipts, debug checks, and platform controls are not daily UX.', tone: 'amber' },
        { kind: 'action', id: 'system_health', title: 'Diagnostics', tone: 'blue', action: { kind: 'propose', label: 'Open health', command: 'open_health', payload: { route: '/health-diagnostics' } } },
      ],
    },
    health: {
      title: 'Health Connect',
      subtitle: 'Android health permissions and food-health context. Still JSON-rendered; native permissions stay behind this surface.',
      components: [
        { kind: 'widget', widget: 'healthConnect', id: 'health_status', title: 'Health Connect status', subtitle: 'Live Android permission status and controls rendered as a JSON widget.', tone: 'blue' },
        { kind: 'action', id: 'health_settings', title: 'Settings', subtitle: 'Return to app controls.', tone: 'plum', action: { kind: 'propose', label: 'Settings', command: 'open_settings', payload: { route: '/settings' } } },
        { kind: 'action', id: 'health_home', title: 'Back to food', tone: 'moss', action: { kind: 'propose', label: 'Open food', command: 'open_food', payload: { route: '/food' } } },
      ],
    },
    notFound: {
      title: 'This moved',
      subtitle: 'Your data is safe. Return to the generated app.',
      components: [
        { kind: 'action', id: 'not_found_home', title: 'Go home', tone: 'moss', action: { kind: 'propose', label: 'Open home', command: 'open_home', payload: { route: '/' } } },
      ],
    },
  },
};
