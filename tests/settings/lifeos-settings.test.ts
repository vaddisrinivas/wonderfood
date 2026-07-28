import { describe, expect, it } from 'vitest';

import {
  defaultLifeOSSettings,
  maskSecret,
  updateLifeOSAiProviderProfile,
  updateLifeOSRuntimePreferences,
} from '@/src/settings/lifeos-settings';

describe('LifeOS settings helpers', () => {
  it('updates persistent theme and density preferences without changing AI keys', () => {
    const base = updateLifeOSAiProviderProfile(defaultLifeOSSettings, 'primary', {
      enabled: true,
      apiKey: 'sk-test-theme-kept',
    });

    const updated = updateLifeOSRuntimePreferences(base, {
      theme: 'dark',
      density: 'compact',
    });

    expect(updated.runtime.theme).toBe('dark');
    expect(updated.runtime.density).toBe('compact');
    expect(updated.ai.primary.apiKey).toBe('sk-test-theme-kept');
  });

  it('keeps, replaces, clears, and masks AI provider API keys safely', () => {
    const withKey = updateLifeOSAiProviderProfile(defaultLifeOSSettings, 'primary', {
      enabled: true,
      provider: 'openai_compatible',
      baseUrl: ' https://api.openai.com/v1 ',
      model: ' gpt-5.4 ',
      apiKey: 'sk-1234567890abcdef',
    });

    expect(withKey.ai.primary.baseUrl).toBe('https://api.openai.com/v1');
    expect(withKey.ai.primary.model).toBe('gpt-5.4');
    expect(maskSecret(withKey.ai.primary.apiKey)).toBe('sk-••••cdef');

    const kept = updateLifeOSAiProviderProfile(withKey, 'primary', {
      model: 'gpt-5.4-mini',
      apiKey: '',
    });
    expect(kept.ai.primary.model).toBe('gpt-5.4-mini');
    expect(kept.ai.primary.apiKey).toBe('sk-1234567890abcdef');

    const replaced = updateLifeOSAiProviderProfile(kept, 'primary', {
      apiKey: 'sk-new-secret',
    });
    expect(replaced.ai.primary.apiKey).toBe('sk-new-secret');

    const cleared = updateLifeOSAiProviderProfile(replaced, 'primary', {
      clearApiKey: true,
    });
    expect(cleared.ai.primary.apiKey).toBe('');
    expect(maskSecret(cleared.ai.primary.apiKey)).toBe('Not set');
  });
});
