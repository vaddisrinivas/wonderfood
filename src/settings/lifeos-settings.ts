import { useEffect, useState } from 'react';

export type AiProviderKind = 'openai_compatible' | 'azure_openai' | 'anthropic';

export type AiProviderProfile = {
  id: 'primary' | 'fallback';
  enabled: boolean;
  provider: AiProviderKind;
  baseUrl: string;
  apiKey: string;
  model: string;
  apiVersion: string;
};

export type TokenProviderSettings = {
  enabled: boolean;
  token: string;
};

export type LifeOSSettings = {
  ai: {
    primary: AiProviderProfile;
    fallback: AiProviderProfile;
  };
  notion: TokenProviderSettings & {
    pageId: string;
    dataSourceIds: string;
  };
  sheets: TokenProviderSettings & {
    workbookId: string;
    sheetName: string;
  };
  postgres: {
    enabled: boolean;
    databaseUrl: string;
  };
  mcp: TokenProviderSettings & {
    url: string;
  };
  runtime: {
    activeDomain: string;
    enabledDomains: string[];
    enabledWorkflows: string[];
    enabledAgents: string[];
    skillInstructions: Record<string, string>;
    schemaOverrides: string;
    visualIdentityOverrides: string;
    automaticSync: boolean;
    syncMinutes: string;
    webSearch: boolean;
    theme: 'system' | 'light' | 'dark';
    density: 'comfortable' | 'compact';
    surfaceConfig: {
      home: {
        sectionOrder: string;
        showNowCard: boolean;
        showReviewQueue: boolean;
        reviewLimit: string;
        showRecentGraph: boolean;
        recentLimit: string;
        showLifeSpaces: boolean;
        showSourceTrust: boolean;
        showControlCard: boolean;
      };
      food: {
        sectionOrder: string;
        showHero: boolean;
        showViewTabs: boolean;
        showManifestBlocks: boolean;
        showCollectionAtlas: boolean;
        dashboardBlocks: string;
        showWidgets: boolean;
        widgets: string;
        showWorkspace: boolean;
        showOperatingViews: boolean;
        operatingViewOrder: string;
        showAttention: boolean;
        showPackageCard: boolean;
        columnLimit: string;
        attentionLimit: string;
      };
      chat: {
        sectionOrder: string;
        showThreads: boolean;
        showSources: boolean;
        sourceLimit: string;
        promptRail: boolean;
        promptPresets: string;
        showContextCard: boolean;
        contextNote: string;
      };
      record: {
        sectionOrder: string;
        mainSectionOrder: string;
        sideSectionOrder: string;
        showHero: boolean;
        showNutrition: boolean;
        showIngredients: boolean;
        showInstructions: boolean;
        showHistory: boolean;
        showEditableNote: boolean;
        showProperties: boolean;
        showRelations: boolean;
        nutritionLimit: string;
        showProvenance: boolean;
      };
      search: {
        sectionOrder: string;
        showHero: boolean;
        showQuickActions: boolean;
        showResults: boolean;
        resultLimit: string;
        emptyHint: string;
      };
      capture: {
        sectionOrder: string;
        showHero: boolean;
        showTypePicker: boolean;
        showEditor: boolean;
        showAttachments: boolean;
        showRouteCard: boolean;
        defaultType: string;
        destinationHint: string;
      };
      sources: {
        sectionOrder: string;
        showHero: boolean;
        showMetrics: boolean;
        showNeedsReview: boolean;
        showDataHomes: boolean;
        showCitations: boolean;
        citationLimit: string;
        showSyncPlan: boolean;
        showPolicy: boolean;
        showConfigLink: boolean;
      };
      health: {
        sectionOrder: string;
        showHero: boolean;
        showStatusCard: boolean;
        showTechnicalReceipt: boolean;
        showDetails: boolean;
      };
    };
  };
};

const STORAGE_KEY = 'lifeos.settings.v1';
const listeners = new Set<(settings: LifeOSSettings) => void>();
const oldFoodSectionOrderDefault = 'hero,tabs,manifest,workspace,attention,widgets,view,package';
const oldFoodWidgetsDefault = 'Food sources|Open profile-configured Food views and provider trust.|blue|/sources\nSkills and MCP|Use the same skills, schemas and tools from app chat or external AI clients.|plum|/settings';
const weakFoodWidgetMarkers = ['Skills and MCP', 'Open profile-configured Food views'];
const oldCaptureDestinationDefault = 'Writes to Food local graph with no network dependency.';

type SecureStoreModule = {
  WHEN_UNLOCKED_THIS_DEVICE_ONLY?: string;
  getItemAsync: (key: string) => Promise<string | null>;
  setItemAsync: (key: string, value: string, options?: Record<string, unknown>) => Promise<void>;
};

function platformOS() {
  try {
    const reactNative = require('react-native') as { Platform?: { OS?: string } };
    return reactNative.Platform?.OS ?? 'node';
  } catch {
    return typeof window === 'undefined' ? 'node' : 'web';
  }
}

function secureStore(): SecureStoreModule | null {
  try {
    return require('expo-secure-store') as SecureStoreModule;
  } catch {
    return null;
  }
}

export const defaultLifeOSSettings: LifeOSSettings = {
  ai: {
    primary: {
      id: 'primary',
      enabled: false,
      provider: 'openai_compatible',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: '',
      model: 'gpt-4o-mini',
      apiVersion: '',
    },
    fallback: {
      id: 'fallback',
      enabled: false,
      provider: 'openai_compatible',
      baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
      apiKey: '',
      model: 'gemini-2.5-flash',
      apiVersion: '',
    },
  },
  notion: {
    enabled: false,
    token: '',
    pageId: '',
    dataSourceIds: '',
  },
  sheets: {
    enabled: false,
    token: '',
    workbookId: '',
    sheetName: 'LifeOS Canonical',
  },
  postgres: {
    enabled: false,
    databaseUrl: '',
  },
  mcp: {
    enabled: false,
    token: '',
    url: '',
  },
  runtime: {
    activeDomain: 'food',
    enabledDomains: ['food'],
    enabledWorkflows: ['meal-plan-to-shopping', 'receipt-to-kitchen', 'weekly-food-reset'],
    enabledAgents: [],
    skillInstructions: {},
    schemaOverrides: '{}',
    visualIdentityOverrides: '{}',
    automaticSync: false,
    syncMinutes: '30',
    webSearch: true,
    theme: 'system',
    density: 'comfortable',
    surfaceConfig: {
      home: {
        sectionOrder: 'now,review,lifeSpaces,recent,sourceTrust,control',
        showNowCard: true,
        showReviewQueue: true,
        reviewLimit: '2',
        showRecentGraph: true,
        recentLimit: '4',
        showLifeSpaces: true,
        showSourceTrust: true,
        showControlCard: false,
      },
      food: {
        sectionOrder: 'tabs,hero,workspace,attention,widgets,view',
        showHero: true,
        showViewTabs: true,
        showManifestBlocks: false,
        showCollectionAtlas: false,
        dashboardBlocks: '',
        showWidgets: true,
        widgets: 'Ask WonderFood|Use pantry, recipes, shopping and nutrition context in one thread.|plum|/chat\nMake grocery list|Turn dinner into a clean shopping list.|blue|/capture',
        showWorkspace: true,
        showOperatingViews: true,
        operatingViewOrder: 'assemblyTable,weekPlan,pantryTimeline,shoppingChecklist',
        showAttention: true,
        showPackageCard: false,
        columnLimit: '4',
        attentionLimit: '3',
      },
      chat: {
        sectionOrder: 'threads,sources,messages,promptRail,context',
        showThreads: true,
        showSources: true,
        sourceLimit: '8',
        promptRail: true,
        promptPresets: 'What can I cook tonight from what I already have?\nShow a table of available vs missing ingredients.\nWhat should I buy for green dal and tandoori chicken?\nSummarize nutrition and previous cooking notes.',
        showContextCard: true,
        contextNote: 'Answers from the active life space first, cites source cards, and uses provider keys only when you enable them.',
      },
      record: {
        sectionOrder: 'hero,nutrition,ingredients,instructions,history,editableNote,properties,relations,provenance',
        mainSectionOrder: 'nutrition,ingredients,instructions,history,editableNote',
        sideSectionOrder: 'properties,relations,provenance',
        showHero: true,
        showNutrition: true,
        showIngredients: true,
        showInstructions: true,
        showHistory: true,
        showEditableNote: true,
        showProperties: true,
        showRelations: true,
        nutritionLimit: '6',
        showProvenance: true,
      },
      search: {
        sectionOrder: 'hero,quickActions,results',
        showHero: true,
        showQuickActions: true,
        showResults: true,
        resultLimit: '8',
        emptyHint: 'Ask LifeOS to search connected sources or the web.',
      },
      capture: {
        sectionOrder: 'hero,typePicker,editor,routeCard',
        showHero: true,
        showTypePicker: true,
        showEditor: true,
        showAttachments: true,
        showRouteCard: true,
        defaultType: 'Note',
        destinationHint: 'Saves to Food on this device with no network dependency.',
      },
      sources: {
        sectionOrder: 'hero,metrics,needsReview,dataHomes,citations,syncPlan,policy,configLink',
        showHero: true,
        showMetrics: true,
        showNeedsReview: true,
        showDataHomes: true,
        showCitations: true,
        citationLimit: '4',
        showSyncPlan: true,
        showPolicy: true,
        showConfigLink: true,
      },
      health: {
        sectionOrder: 'hero,status,details',
        showHero: true,
        showStatusCard: true,
        showTechnicalReceipt: false,
        showDetails: true,
      },
    },
  },
};

function normalizeProfile(
  id: AiProviderProfile['id'],
  input: Partial<AiProviderProfile> | undefined,
  fallback: AiProviderProfile,
): AiProviderProfile {
  const provider = input?.provider;
  return {
    id,
    enabled: Boolean(input?.enabled),
    provider:
      provider === 'azure_openai' || provider === 'anthropic' || provider === 'openai_compatible'
        ? provider
        : fallback.provider,
    baseUrl: typeof input?.baseUrl === 'string' ? input.baseUrl.trim() : fallback.baseUrl,
    apiKey: typeof input?.apiKey === 'string' ? input.apiKey.trim() : '',
    model: typeof input?.model === 'string' ? input.model.trim() : fallback.model,
    apiVersion: typeof input?.apiVersion === 'string' ? input.apiVersion.trim() : fallback.apiVersion,
  };
}

function normalizeSettings(input: Partial<LifeOSSettings> | null): LifeOSSettings {
  const runtime = input?.runtime;
  const strings = (value: unknown, fallback: string[]) =>
    Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : fallback;
  const skillInstructions =
    runtime?.skillInstructions && typeof runtime.skillInstructions === 'object'
      ? Object.fromEntries(
          Object.entries(runtime.skillInstructions).filter(
            (entry): entry is [string, string] => typeof entry[1] === 'string',
          ),
        )
      : {};
  return {
    ai: {
      primary: normalizeProfile('primary', input?.ai?.primary, defaultLifeOSSettings.ai.primary),
      fallback: normalizeProfile('fallback', input?.ai?.fallback, defaultLifeOSSettings.ai.fallback),
    },
    notion: {
      enabled: Boolean(input?.notion?.enabled),
      token: typeof input?.notion?.token === 'string' ? input.notion.token.trim() : '',
      pageId: typeof input?.notion?.pageId === 'string' ? input.notion.pageId.trim() : '',
      dataSourceIds: typeof input?.notion?.dataSourceIds === 'string' ? input.notion.dataSourceIds.trim() : '',
    },
    sheets: {
      enabled: Boolean(input?.sheets?.enabled),
      token: typeof input?.sheets?.token === 'string' ? input.sheets.token.trim() : '',
      workbookId: typeof input?.sheets?.workbookId === 'string' ? input.sheets.workbookId.trim() : '',
      sheetName: typeof input?.sheets?.sheetName === 'string' ? input.sheets.sheetName.trim() : 'LifeOS Canonical',
    },
    postgres: {
      enabled: Boolean(input?.postgres?.enabled),
      databaseUrl: typeof input?.postgres?.databaseUrl === 'string' ? input.postgres.databaseUrl.trim() : '',
    },
    mcp: {
      enabled: Boolean(input?.mcp?.enabled),
      token: typeof input?.mcp?.token === 'string' ? input.mcp.token.trim() : '',
      url: typeof input?.mcp?.url === 'string' ? input.mcp.url.trim() : '',
    },
    runtime: {
      activeDomain: typeof runtime?.activeDomain === 'string' ? runtime.activeDomain.trim() || 'food' : 'food',
      enabledDomains: strings(runtime?.enabledDomains, ['food']),
      enabledWorkflows: strings(runtime?.enabledWorkflows, defaultLifeOSSettings.runtime.enabledWorkflows),
      enabledAgents: strings(runtime?.enabledAgents, []),
      skillInstructions,
      schemaOverrides: typeof runtime?.schemaOverrides === 'string' ? runtime.schemaOverrides : '{}',
      visualIdentityOverrides: typeof runtime?.visualIdentityOverrides === 'string' ? runtime.visualIdentityOverrides : '{}',
      automaticSync: Boolean(runtime?.automaticSync),
      syncMinutes: typeof runtime?.syncMinutes === 'string' ? runtime.syncMinutes.trim() || '30' : '30',
      webSearch: runtime?.webSearch !== false,
      theme: runtime?.theme === 'light' || runtime?.theme === 'dark' ? runtime.theme : 'system',
      density: runtime?.density === 'compact' ? 'compact' : 'comfortable',
      surfaceConfig: normalizeSurfaceConfig(runtime?.surfaceConfig),
    },
  };
}

function normalizePositiveString(value: unknown, fallback: string) {
  const text = typeof value === 'string' ? value.trim() : '';
  const parsed = Number.parseInt(text, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? String(parsed) : fallback;
}

function normalizeOrderString(value: unknown, fallback: string) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text.length ? text : fallback;
}

function normalizeDefaultOrder(value: unknown, fallback: string, legacy: string) {
  const text = normalizeOrderString(value, fallback);
  return text === legacy ? fallback : text;
}

function normalizeFoodSectionOrder(value: unknown, fallback: string) {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text || text === oldFoodSectionOrderDefault || text === 'hero,tabs,widgets,workspace,attention,view,package') {
    return fallback;
  }
  const parts = text.split(',').map((part) => part.trim()).filter(Boolean);
  if (parts.includes('collections')) return text;
  const anchor = parts.includes('manifest') ? parts.indexOf('manifest') : parts.includes('hero') ? parts.indexOf('hero') : 0;
  return [...parts.slice(0, anchor + 1), 'collections', ...parts.slice(anchor + 1)].join(',');
}

function normalizeSurfaceConfig(value: unknown): LifeOSSettings['runtime']['surfaceConfig'] {
  const config = value && typeof value === 'object' ? value as Partial<LifeOSSettings['runtime']['surfaceConfig']> : {};
  const defaults = defaultLifeOSSettings.runtime.surfaceConfig;
  return {
    home: {
      sectionOrder: normalizeDefaultOrder(config.home?.sectionOrder, defaults.home.sectionOrder, 'now,review,lifeSpaces,recent,sourceTrust,control'),
      showNowCard: config.home?.showNowCard !== false,
      showReviewQueue: config.home?.showReviewQueue !== false,
      reviewLimit: normalizePositiveString(config.home?.reviewLimit, defaults.home.reviewLimit),
      showRecentGraph: config.home?.showRecentGraph !== false,
      recentLimit: normalizePositiveString(config.home?.recentLimit, defaults.home.recentLimit),
      showLifeSpaces: config.home?.showLifeSpaces !== false,
      showSourceTrust: config.home?.showSourceTrust !== false,
      showControlCard: config.home?.showControlCard !== false,
    },
    food: {
      sectionOrder: normalizeFoodSectionOrder(config.food?.sectionOrder, defaults.food.sectionOrder),
      showHero: config.food?.showHero !== false,
      showViewTabs: config.food?.showViewTabs !== false,
      showManifestBlocks: config.food?.showManifestBlocks === true,
      showCollectionAtlas: config.food?.showCollectionAtlas === true,
      dashboardBlocks: typeof config.food?.dashboardBlocks === 'string' ? config.food.dashboardBlocks : defaults.food.dashboardBlocks,
      showWidgets: config.food?.showWidgets !== false,
      widgets: typeof config.food?.widgets === 'string' && config.food.widgets !== oldFoodWidgetsDefault && !weakFoodWidgetMarkers.some((marker) => config.food?.widgets.includes(marker)) ? config.food.widgets : defaults.food.widgets,
      showWorkspace: config.food?.showWorkspace !== false,
      showOperatingViews: config.food?.showOperatingViews !== false,
      operatingViewOrder: normalizeOrderString(config.food?.operatingViewOrder, defaults.food.operatingViewOrder),
      showAttention: config.food?.showAttention !== false,
      showPackageCard: config.food?.showPackageCard === true,
      columnLimit: normalizePositiveString(config.food?.columnLimit, defaults.food.columnLimit),
      attentionLimit: normalizePositiveString(config.food?.attentionLimit, defaults.food.attentionLimit),
    },
    chat: {
      sectionOrder: normalizeOrderString(config.chat?.sectionOrder, defaults.chat.sectionOrder),
      showThreads: config.chat?.showThreads !== false,
      showSources: config.chat?.showSources !== false,
      sourceLimit: normalizePositiveString(config.chat?.sourceLimit, defaults.chat.sourceLimit),
      promptRail: config.chat?.promptRail !== false,
      promptPresets: typeof config.chat?.promptPresets === 'string' ? config.chat.promptPresets : defaults.chat.promptPresets,
      showContextCard: config.chat?.showContextCard !== false,
      contextNote: typeof config.chat?.contextNote === 'string' ? config.chat.contextNote : defaults.chat.contextNote,
    },
    record: {
      sectionOrder: normalizeOrderString(config.record?.sectionOrder, defaults.record.sectionOrder),
      mainSectionOrder: normalizeOrderString(config.record?.mainSectionOrder, defaults.record.mainSectionOrder),
      sideSectionOrder: normalizeOrderString(config.record?.sideSectionOrder, defaults.record.sideSectionOrder),
      showHero: config.record?.showHero !== false,
      showNutrition: config.record?.showNutrition !== false,
      showIngredients: config.record?.showIngredients !== false,
      showInstructions: config.record?.showInstructions !== false,
      showHistory: config.record?.showHistory !== false,
      showEditableNote: config.record?.showEditableNote !== false,
      showProperties: config.record?.showProperties !== false,
      showRelations: config.record?.showRelations !== false,
      nutritionLimit: normalizePositiveString(config.record?.nutritionLimit, defaults.record.nutritionLimit),
      showProvenance: config.record?.showProvenance !== false,
    },
    search: {
      sectionOrder: normalizeOrderString(config.search?.sectionOrder, defaults.search.sectionOrder),
      showHero: config.search?.showHero !== false,
      showQuickActions: config.search?.showQuickActions !== false,
      showResults: config.search?.showResults !== false,
      resultLimit: normalizePositiveString(config.search?.resultLimit, defaults.search.resultLimit),
      emptyHint: typeof config.search?.emptyHint === 'string' ? config.search.emptyHint : defaults.search.emptyHint,
    },
    capture: {
      sectionOrder: normalizeOrderString(config.capture?.sectionOrder, defaults.capture.sectionOrder),
      showHero: config.capture?.showHero !== false,
      showTypePicker: config.capture?.showTypePicker !== false,
      showEditor: config.capture?.showEditor !== false,
      showAttachments: config.capture?.showAttachments !== false,
      showRouteCard: config.capture?.showRouteCard !== false,
      defaultType: typeof config.capture?.defaultType === 'string' ? config.capture.defaultType : defaults.capture.defaultType,
      destinationHint: typeof config.capture?.destinationHint === 'string' && config.capture.destinationHint !== oldCaptureDestinationDefault ? config.capture.destinationHint : defaults.capture.destinationHint,
    },
    sources: {
      sectionOrder: normalizeOrderString(config.sources?.sectionOrder, defaults.sources.sectionOrder),
      showHero: config.sources?.showHero !== false,
      showMetrics: config.sources?.showMetrics !== false,
      showNeedsReview: config.sources?.showNeedsReview !== false,
      showDataHomes: config.sources?.showDataHomes !== false,
      showCitations: config.sources?.showCitations !== false,
      citationLimit: normalizePositiveString(config.sources?.citationLimit, defaults.sources.citationLimit),
      showSyncPlan: config.sources?.showSyncPlan !== false,
      showPolicy: config.sources?.showPolicy !== false,
      showConfigLink: config.sources?.showConfigLink !== false,
    },
    health: {
      sectionOrder: normalizeOrderString(config.health?.sectionOrder, defaults.health.sectionOrder),
      showHero: config.health?.showHero !== false,
      showStatusCard: config.health?.showStatusCard !== false,
      showTechnicalReceipt: Boolean(config.health?.showTechnicalReceipt),
      showDetails: config.health?.showDetails !== false,
    },
  };
}

async function readRaw(): Promise<string | null> {
  const os = platformOS();
  if (os === 'web') {
    return typeof localStorage === 'undefined' ? null : localStorage.getItem(STORAGE_KEY);
  }
  if (os === 'node') {
    return null;
  }
  return secureStore()?.getItemAsync(STORAGE_KEY) ?? null;
}

async function writeRaw(value: string): Promise<void> {
  const os = platformOS();
  if (os === 'web') {
    if (typeof localStorage !== 'undefined') localStorage.setItem(STORAGE_KEY, value);
    return;
  }
  if (os === 'node') {
    return;
  }
  const store = secureStore();
  if (!store) {
    return;
  }
  await store.setItemAsync(STORAGE_KEY, value, {
    keychainAccessible: store.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function loadLifeOSSettings(): Promise<LifeOSSettings> {
  try {
    const raw = await readRaw();
    return raw ? normalizeSettings(JSON.parse(raw) as Partial<LifeOSSettings>) : defaultLifeOSSettings;
  } catch {
    return defaultLifeOSSettings;
  }
}

export async function saveLifeOSSettings(settings: LifeOSSettings): Promise<LifeOSSettings> {
  const normalized = normalizeSettings(settings);
  await writeRaw(JSON.stringify(normalized));
  listeners.forEach((listener) => listener(normalized));
  return normalized;
}

export function subscribeLifeOSSettings(listener: (settings: LifeOSSettings) => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function useLifeOSSettingsSnapshot(): LifeOSSettings {
  const [settings, setSettings] = useState(defaultLifeOSSettings);

  useEffect(() => {
    let cancelled = false;
    void loadLifeOSSettings().then((value) => {
      if (!cancelled) setSettings(value);
    });
    const unsubscribe = subscribeLifeOSSettings(setSettings);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  return settings;
}

export function usableAiProfiles(settings: LifeOSSettings): AiProviderProfile[] {
  return [settings.ai.primary, settings.ai.fallback].filter(
    (profile) =>
      profile.enabled &&
      profile.baseUrl.trim().length > 0 &&
      profile.apiKey.trim().length > 0 &&
      profile.model.trim().length > 0,
  );
}

export function providerLabel(profile: AiProviderProfile): string {
  if (profile.provider === 'azure_openai') return `Azure · ${profile.model || 'deployment'}`;
  if (profile.provider === 'anthropic') return `Anthropic · ${profile.model || 'model'}`;
  const base = profile.baseUrl.toLowerCase();
  const vendor = base.includes('openrouter')
    ? 'OpenRouter'
    : base.includes('generativelanguage.googleapis.com')
      ? 'Gemini'
      : base.includes('api.openai.com')
        ? 'OpenAI'
        : 'OpenAI-compatible';
  return `${vendor} · ${profile.model || 'model'}`;
}
