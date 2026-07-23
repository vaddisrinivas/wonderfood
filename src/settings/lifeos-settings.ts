import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

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
    automaticSync: boolean;
    syncMinutes: string;
    webSearch: boolean;
    theme: 'system' | 'light' | 'dark';
    density: 'comfortable' | 'compact';
  };
};

const STORAGE_KEY = 'lifeos.settings.v1';

export const defaultLifeOSSettings: LifeOSSettings = {
  ai: {
    primary: {
      id: 'primary',
      enabled: false,
      provider: 'openai_compatible',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: '',
      model: 'gpt-5.4-mini',
      apiVersion: '',
    },
    fallback: {
      id: 'fallback',
      enabled: false,
      provider: 'anthropic',
      baseUrl: 'https://api.anthropic.com',
      apiKey: '',
      model: 'claude-sonnet-4-6',
      apiVersion: '2023-06-01',
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
    automaticSync: false,
    syncMinutes: '30',
    webSearch: true,
    theme: 'system',
    density: 'comfortable',
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
      automaticSync: Boolean(runtime?.automaticSync),
      syncMinutes: typeof runtime?.syncMinutes === 'string' ? runtime.syncMinutes.trim() || '30' : '30',
      webSearch: runtime?.webSearch !== false,
      theme: runtime?.theme === 'light' || runtime?.theme === 'dark' ? runtime.theme : 'system',
      density: runtime?.density === 'compact' ? 'compact' : 'comfortable',
    },
  };
}

async function readRaw(): Promise<string | null> {
  if (Platform.OS === 'web') {
    return typeof localStorage === 'undefined' ? null : localStorage.getItem(STORAGE_KEY);
  }
  return SecureStore.getItemAsync(STORAGE_KEY);
}

async function writeRaw(value: string): Promise<void> {
  if (Platform.OS === 'web') {
    if (typeof localStorage !== 'undefined') localStorage.setItem(STORAGE_KEY, value);
    return;
  }
  await SecureStore.setItemAsync(STORAGE_KEY, value, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
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
  return normalized;
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
