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
          title: 'Health and diagnostics',
          subtitle: 'Check runtime health only when something feels wrong.',
          tone: 'amber',
          action: { kind: 'propose', label: 'Open diagnostics', command: 'open_health', payload: { route: '/health-diagnostics' } },
        },
      ],
    },
  },
};
