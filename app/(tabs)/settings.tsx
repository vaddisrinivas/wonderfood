import { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import { Link } from 'expo-router';

import { testDirectModelProfile } from '@/src/chat/direct-provider';
import { Card, Page, PageHeader, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import {
  HealthConnectStatus,
  getLifeOSHealthStatus,
  openLifeOSHealthSettings,
  requestLifeOSHealthPermissions,
} from '@/src/health/connect';
import {
  AiProviderKind,
  AiProviderProfile,
  LifeOSSettings,
  defaultLifeOSSettings,
  loadLifeOSSettings,
  providerLabel,
  saveLifeOSSettings,
} from '@/src/settings/lifeos-settings';
import { colors, radius, useLifeOSTheme } from '@/src/theme';

const providerOptions: Array<{ id: AiProviderKind; label: string }> = [
  { id: 'openai_compatible', label: 'OpenAI-compatible' },
  { id: 'azure_openai', label: 'Azure OpenAI' },
  { id: 'anthropic', label: 'Anthropic' },
];

const providerPresets: Array<{
  label: string;
  detail: string;
  patch: Pick<AiProviderProfile, 'provider' | 'baseUrl' | 'model' | 'apiVersion'>;
}> = [
  {
    label: 'OpenAI',
    detail: 'Common default',
    patch: { provider: 'openai_compatible', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini', apiVersion: '' },
  },
  {
    label: 'Gemini',
    detail: 'OpenAI-compatible',
    patch: { provider: 'openai_compatible', baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai', model: 'gemini-2.5-flash', apiVersion: '' },
  },
  {
    label: 'Anthropic',
    detail: 'Bring your key',
    patch: { provider: 'anthropic', baseUrl: 'https://api.anthropic.com', model: 'claude-haiku-4-5-20251001', apiVersion: '2023-06-01' },
  },
  {
    label: 'Azure',
    detail: 'Bring deployment',
    patch: { provider: 'azure_openai', baseUrl: '', model: '', apiVersion: '2024-10-21' },
  },
];

function defaultsFor(provider: AiProviderKind) {
  if (provider === 'anthropic') {
    return { baseUrl: 'https://api.anthropic.com', model: 'claude-haiku-4-5-20251001', apiVersion: '2023-06-01' };
  }
  if (provider === 'azure_openai') {
    return { baseUrl: '', model: '', apiVersion: '2024-10-21' };
  }
  return { baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini', apiVersion: '' };
}

export default function SettingsScreen() {
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const theme = useLifeOSTheme();
  const [settings, setSettings] = useState<LifeOSSettings>(defaultLifeOSSettings);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [testing, setTesting] = useState<AiProviderProfile['id'] | null>(null);
  const [healthStatus, setHealthStatus] = useState<HealthConnectStatus | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [advancedPanel, setAdvancedPanel] = useState<'ai' | 'data' | 'health' | 'app' | 'privacy' | null>(null);
  const enabledAiCount = [settings.ai.primary, settings.ai.fallback].filter((profile) => profile.enabled).length;
  const enabledSourceCount = [settings.notion.enabled, settings.sheets.enabled, settings.postgres.enabled, settings.mcp.enabled].filter(Boolean).length;
  const healthAvailable = healthStatus?.availability === 'available';
  const healthConnected = (healthStatus?.granted.length ?? 0) > 0;
  const updateFoodSurface = (patch: Partial<typeof settings.runtime.surfaceConfig.food>) => {
    setSettings((current) => ({
      ...current,
      runtime: {
        ...current.runtime,
        surfaceConfig: {
          ...current.runtime.surfaceConfig,
          food: {
            ...current.runtime.surfaceConfig.food,
            ...patch,
          },
        },
      },
    }));
  };

  useEffect(() => {
    let cancelled = false;
    void loadLifeOSSettings().then((value) => {
      if (!cancelled) {
        setSettings(value);
        setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    void getLifeOSHealthStatus().then((value) => {
      if (!cancelled) setHealthStatus(value);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const updateProfile = (id: AiProviderProfile['id'], patch: Partial<AiProviderProfile>) => {
    setSettings((current) => ({
      ...current,
      ai: {
        ...current.ai,
        [id]: { ...current.ai[id], ...patch },
      },
    }));
  };

  const chooseProvider = (id: AiProviderProfile['id'], provider: AiProviderKind) => {
    const profile = settings.ai[id];
    const nextDefaults = defaultsFor(provider);
    updateProfile(id, {
      provider,
      baseUrl: !profile.baseUrl || profile.baseUrl === defaultsFor(profile.provider).baseUrl ? nextDefaults.baseUrl : profile.baseUrl,
      model: !profile.model || profile.model === defaultsFor(profile.provider).model ? nextDefaults.model : profile.model,
      apiVersion: nextDefaults.apiVersion,
    });
  };

  const applyProviderPreset = (id: AiProviderProfile['id'], patch: Pick<AiProviderProfile, 'provider' | 'baseUrl' | 'model' | 'apiVersion'>) => {
    updateProfile(id, patch);
  };

  const save = async () => {
    setSaving(true);
    setNotice('');
    try {
      const next = await saveLifeOSSettings(settings);
      setSettings(next);
      setNotice(Platform.OS === 'web' ? 'Saved in this browser.' : 'Saved in encrypted device storage.');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not save settings.');
    } finally {
      setSaving(false);
    }
  };

  const test = async (id: AiProviderProfile['id']) => {
    const profile = settings.ai[id];
    setTesting(id);
    setNotice('');
    try {
      setNotice(await testDirectModelProfile(profile));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Connection failed.');
    } finally {
      setTesting(null);
    }
  };

  return (
    <Page>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.fill}>
        <ScrollView contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            {compact ? (
              <>
                <View style={styles.mobileHeader}>
                  <View>
                    <Text maxFontSizeMultiplier={1.2} style={[styles.mobileTitle, { color: theme.colors.ink }]}>Settings</Text>
                    <Text maxFontSizeMultiplier={1.2} style={[styles.mobileSubtitle, { color: theme.colors.muted }]}>Food · {settings.runtime.density}</Text>
                  </View>
                  <Pill tone={settings.ai.primary.enabled ? 'moss' : 'blue'}>
                    {settings.ai.primary.enabled ? 'AI ready' : 'Set up AI'}
                  </Pill>
                </View>
                <Card style={styles.quickStatus}>
                  <SettingsStatusRow
                    label="Wonder AI"
                    value={enabledAiCount ? (enabledAiCount === 1 ? 'Ready' : `${enabledAiCount} ready`) : 'Not connected'}
                    ready={enabledAiCount > 0}
                  />
                  <SettingsStatusRow
                    label="Food data"
                    value={enabledSourceCount ? `${enabledSourceCount} connected` : 'On this phone'}
                    ready={enabledSourceCount > 0}
                  />
                  <SettingsStatusRow
                    label="Health"
                    value={Platform.OS === 'android' ? (healthConnected ? 'Connected' : healthAvailable ? 'Available' : 'Unavailable') : 'Android only'}
                    ready={healthConnected}
                  />
                </Card>
              </>
            ) : (
              <>
                <View style={styles.contextBar}>
                  <View>
                    <Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / CONNECTIONS</Text>
                    <Text style={[styles.context, { color: theme.colors.muted }]}>Food workspace settings</Text>
                  </View>
                  <Pill tone={settings.ai.primary.enabled ? 'moss' : 'blue'}>
                    {settings.ai.primary.enabled ? 'AI READY' : 'LOCAL FIRST'}
                  </Pill>
                </View>

                <PageHeader
                  eyebrow="Local first"
                  title="Configure food, sources, and app preferences."
                  subtitle="Keep local records on top, and open Advanced for external connectors."
                />

                <SectionTitle title="Status" />
                <View style={styles.statusGrid}>
                  <SettingsStatusCard
                    tone={enabledAiCount ? 'moss' : 'blue'}
                    label="AI"
                    title={enabledAiCount ? `${enabledAiCount} provider${enabledAiCount === 1 ? '' : 's'} enabled` : 'Local answers first'}
                    detail={enabledAiCount ? 'Primary/fallback model routing is ready for Chat.' : 'Paste a key when you want live model answers.'}
                  />
                  <SettingsStatusCard
                    tone={enabledSourceCount ? 'plum' : 'amber'}
                    label="Food data"
                    title={enabledSourceCount ? `${enabledSourceCount} source${enabledSourceCount === 1 ? '' : 's'} enabled` : 'No external sources'}
                    detail="External food connectors are optional and available in Advanced."
                  />
                  <SettingsStatusCard
                    tone="moss"
                    label="App behavior"
                    title={`${settings.runtime.activeDomain} · ${settings.runtime.density}`}
                    detail="Active domain, screen order, card counts and assistant behavior live in Customize."
                  />
                  <SettingsStatusCard
                    tone={healthConnected ? 'moss' : 'blue'}
                    label="Health"
                    title={Platform.OS === 'android' ? (healthConnected ? 'Health Connect ready' : healthAvailable ? 'Health Connect available' : 'Health Connect unavailable') : 'Android-only check'}
                    detail={healthStatus?.message ?? 'Health Connect is checked locally on device.'}
                  />
                </View>

                <Card tone="moss" style={styles.principleCard}>
                  <Text style={[styles.principleKicker, { color: theme.colors.moss }]}>PORTABLE</Text>
                  <Text style={[styles.principleTitle, { color: theme.colors.ink }]}>Direct on device. Fallback when needed.</Text>
                  <Text style={[styles.principleBody, { color: theme.colors.muted }]}>
                    The app tries Primary, then Fallback, then stays local. Direct tokens stay on this device.
                  </Text>
                </Card>
              </>
            )}

            <Card tone="moss" style={styles.advancedCard}>
              <Pressable accessibilityRole="button" onPress={() => {
                setShowAdvanced((current) => !current);
                setAdvancedPanel(null);
              }} style={({ pressed }) => [styles.configButton, pressed && styles.pressed, { backgroundColor: theme.colors.paper, borderWidth: 1, borderColor: theme.colors.line }]}>
                <View style={styles.switchCopy}>
                  <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>Advanced</Text>
                  <Text style={[styles.cardBody, { color: theme.colors.muted }]}>AI, data sources and app customization.</Text>
                </View>
                <Text style={[styles.configButtonArrow, { color: theme.colors.ink }]}>{showAdvanced ? '-' : '+'}</Text>
              </Pressable>
            </Card>

            {showAdvanced && compact ? (
              <Card style={styles.advancedMenu}>
                <SettingsMenuRow label="AI provider" value={enabledAiCount ? 'Ready' : 'Set up'} active={advancedPanel === 'ai'} onPress={() => setAdvancedPanel('ai')} />
                <SettingsMenuRow label="Data connections" value={enabledSourceCount ? `${enabledSourceCount} on` : 'Optional'} active={advancedPanel === 'data'} onPress={() => setAdvancedPanel('data')} />
                <SettingsMenuRow label="Health Connect" value={healthConnected ? 'Connected' : healthAvailable ? 'Available' : 'Unavailable'} active={advancedPanel === 'health'} onPress={() => setAdvancedPanel('health')} />
                <SettingsMenuRow label="Customize app" value="Open builder" active={advancedPanel === 'app'} onPress={() => setAdvancedPanel('app')} />
                <SettingsMenuRow label="Privacy & storage" value="Device secured" active={advancedPanel === 'privacy'} onPress={() => setAdvancedPanel('privacy')} />
              </Card>
            ) : null}

            {showAdvanced ? (
              <>
                {!compact || advancedPanel === 'ai' ? (
                  <>
                    <SectionTitle title="AI provider" />
                    <View style={[styles.providerGrid, compact && styles.stack]}>
                  <ProviderCard
                    title="Primary"
                    subtitle="Used first for Chat and AI-assisted capture."
                    profile={settings.ai.primary}
                    onChange={(patch) => updateProfile('primary', patch)}
                    onChoose={(provider) => chooseProvider('primary', provider)}
                    onPreset={(patch) => applyProviderPreset('primary', patch)}
                    onTest={() => void test('primary')}
                    testing={testing === 'primary'}
                  />
                  <ProviderCard
                    title="Fallback"
                    subtitle="Used only when Primary is unavailable."
                    profile={settings.ai.fallback}
                    onChange={(patch) => updateProfile('fallback', patch)}
                    onChoose={(provider) => chooseProvider('fallback', provider)}
                    onPreset={(patch) => applyProviderPreset('fallback', patch)}
                    onTest={() => void test('fallback')}
                    testing={testing === 'fallback'}
                  />
                    </View>
                  </>
                ) : null}

                {!compact || advancedPanel === 'health' ? (
                  <>
                <SectionTitle title="Health" />
                <Card tone="plum" style={styles.healthCard}>
                  <View style={styles.switchRow}>
                    <View style={styles.switchCopy}>
                      <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>Android Health Connect</Text>
                      <Text style={[styles.cardBody, { color: theme.colors.muted }]}>{healthStatus?.message ?? 'Checking Health Connect...'}</Text>
                    </View>
                    <Pill tone={healthStatus?.availability === 'available' ? 'moss' : 'blue'}>
                      {Platform.OS === 'android' ? `${healthStatus?.granted.length ?? 0} scopes` : 'Android only'}
                    </Pill>
                  </View>
                  <View style={styles.healthActions}>
                    <Pressable
                      accessibilityRole="button"
                      onPress={async () => {
                        const next = await requestLifeOSHealthPermissions();
                        setHealthStatus(next);
                        setNotice(next.message);
                      }}
                      style={({ pressed }) => [styles.configButton, { backgroundColor: theme.colors.ink }, pressed && styles.pressed]}
                    >
                      <Text style={[styles.configButtonText, { color: theme.colors.paper }]}>Grant permissions</Text>
                      <Text style={[styles.configButtonArrow, { color: theme.colors.paper }]}>→</Text>
                    </Pressable>
                    <Pressable
                      accessibilityRole="button"
                      onPress={async () => {
                        const opened = await openLifeOSHealthSettings();
                        setNotice(opened ? 'Opened Health Connect settings.' : 'Health Connect settings are available on Android only.');
                      }}
                      style={({ pressed }) => [styles.secondaryButton, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}
                    >
                      <Text style={[styles.secondaryButtonText, { color: theme.colors.ink }]}>Open system settings</Text>
                    </Pressable>
                  </View>
                </Card>
                  </>
                ) : null}

                {!compact || advancedPanel === 'data' ? (
                  <>
                <SectionTitle title="Data connections" />
                <View style={[styles.providerGrid, compact && styles.stack]}>
                  <SourceCard
                    title="Notion"
                    detail="Food notes, pages, and lists."
                    enabled={settings.notion.enabled}
                    onEnabled={(enabled) => setSettings((current) => ({ ...current, notion: { ...current.notion, enabled } }))}
                  >
                    <Field label="Integration token" value={settings.notion.token} placeholder="ntn_…" secureTextEntry onChangeText={(token) => setSettings((current) => ({ ...current, notion: { ...current.notion, token } }))} />
                    <Field label="Root page ID" value={settings.notion.pageId} placeholder="Page ID" onChangeText={(pageId) => setSettings((current) => ({ ...current, notion: { ...current.notion, pageId } }))} />
                    <Field label="Page IDs" value={settings.notion.dataSourceIds} placeholder="Comma-separated IDs" onChangeText={(dataSourceIds) => setSettings((current) => ({ ...current, notion: { ...current.notion, dataSourceIds } }))} />
                  </SourceCard>
                  <SourceCard
                    title="Google Sheets"
                    detail="Kitchen tables and shopping backups."
                    enabled={settings.sheets.enabled}
                    onEnabled={(enabled) => setSettings((current) => ({ ...current, sheets: { ...current.sheets, enabled } }))}
                  >
                    <Field label="Google access token" value={settings.sheets.token} placeholder="Paste or connect OAuth token" secureTextEntry onChangeText={(token) => setSettings((current) => ({ ...current, sheets: { ...current.sheets, token } }))} />
                    <Field label="Workbook ID" value={settings.sheets.workbookId} placeholder="Spreadsheet ID" onChangeText={(workbookId) => setSettings((current) => ({ ...current, sheets: { ...current.sheets, workbookId } }))} />
                    <Field label="Kitchen tab" value={settings.sheets.sheetName} placeholder="LifeOS Canonical" onChangeText={(sheetName) => setSettings((current) => ({ ...current, sheets: { ...current.sheets, sheetName } }))} />
                  </SourceCard>
                </View>

                <View style={[styles.providerGrid, compact && styles.stack]}>
                  <SourceCard
                    title="Postgres"
                    detail="Optional durable storage for advanced exports."
                    enabled={settings.postgres.enabled}
                    onEnabled={(enabled) => setSettings((current) => ({ ...current, postgres: { ...current.postgres, enabled } }))}
                  >
                    <Field label="Database URL" value={settings.postgres.databaseUrl} placeholder="postgresql://…" secureTextEntry onChangeText={(databaseUrl) => setSettings((current) => ({ ...current, postgres: { ...current.postgres, databaseUrl } }))} />
                  </SourceCard>
                  <SourceCard
                    title="MCP"
                    detail="Optional command bridge when you run custom agents."
                    enabled={settings.mcp.enabled}
                    onEnabled={(enabled) => setSettings((current) => ({ ...current, mcp: { ...current.mcp, enabled } }))}
                  >
                    <Field label="MCP URL" value={settings.mcp.url} placeholder="https://…/mcp" onChangeText={(url) => setSettings((current) => ({ ...current, mcp: { ...current.mcp, url } }))} />
                    <Field label="MCP token" value={settings.mcp.token} placeholder="Optional private token" secureTextEntry onChangeText={(token) => setSettings((current) => ({ ...current, mcp: { ...current.mcp, token } }))} />
                  </SourceCard>
                </View>
                  </>
                ) : null}

                {!compact || advancedPanel === 'app' ? (
                  <>
                <SectionTitle title="Customize app" />
                <Card style={styles.connectorCard}>
                  <View style={styles.switchRow}>
                    <View style={styles.switchCopy}>
                      <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>Show Food advanced tools</Text>
                      <Text style={[styles.cardBody, { color: theme.colors.muted }]}>Reveal setup helpers, sample actions, and maintenance tools.</Text>
                    </View>
                    <Switch
                      accessibilityLabel="Show Food advanced tools"
                      value={settings.runtime.surfaceConfig.food.showPackageCard}
                      onValueChange={(enabled) => updateFoodSurface({ showPackageCard: enabled })}
                      trackColor={{ false: theme.colors.line, true: theme.colors.mossSoft }}
                      thumbColor={settings.runtime.surfaceConfig.food.showPackageCard ? theme.colors.moss : theme.colors.muted}
                    />
                  </View>
                </Card>

                <Card style={styles.connectorCard}>
                  <View style={styles.switchRow}>
                    <View style={styles.switchCopy}>
                      <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>App builder</Text>
                      <Text style={[styles.cardBody, { color: theme.colors.muted }]}>Change tables, screens, rules, workflows and visual style.</Text>
                    </View>
                    <Pill tone="plum">CONFIG STUDIO</Pill>
                  </View>
                  <Link href="/config" asChild>
                    <Pressable accessibilityRole="button" style={({ pressed }) => [styles.configButton, { backgroundColor: theme.colors.ink }, pressed && styles.pressed]}>
                      <Text style={[styles.configButtonText, { color: theme.colors.paper }]}>Customize app</Text>
                      <Text style={[styles.configButtonArrow, { color: theme.colors.paper }]}>→</Text>
                    </Pressable>
                  </Link>
                </Card>
                  </>
                ) : null}

                {!compact || advancedPanel === 'privacy' ? (
                <Card tone="blue" style={styles.securityCard}>
                  <Text style={[styles.securityTitle, { color: theme.colors.blue }]}>{Platform.OS === 'web' ? 'Browser storage notice' : 'Device-secured credentials'}</Text>
                  <Text style={[styles.securityBody, { color: theme.colors.muted }]}>
                    {Platform.OS === 'web'
                      ? 'Web saves credentials only in this browser. For sensitive keys, prefer Android/iOS encrypted storage.'
                      : 'Provider keys are stored with the operating system secure store and are not bundled into the app or committed to the repository.'}
                  </Text>
                </Card>
                ) : null}
              </>
            ) : null}

            {notice ? <Text accessibilityLiveRegion="polite" style={[styles.notice, { color: theme.colors.moss }]}>{notice}</Text> : null}
            {showAdvanced && (!compact || advancedPanel !== null) ? (
              <Pressable
                accessibilityRole="button"
                disabled={loading || saving}
                onPress={() => void save()}
                style={({ pressed }) => [styles.save, { backgroundColor: theme.colors.ink }, (loading || saving) && styles.disabled, pressed && styles.pressed]}
              >
                <Text style={[styles.saveText, { color: theme.colors.paper }]}>{saving ? 'Saving…' : loading ? 'Loading…' : 'Save changes'}</Text>
              </Pressable>
            ) : null}
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Page>
  );
}

function ProviderCard(props: {
  title: string;
  subtitle: string;
  profile: AiProviderProfile;
  onChange: (patch: Partial<AiProviderProfile>) => void;
  onChoose: (provider: AiProviderKind) => void;
  onPreset: (patch: Pick<AiProviderProfile, 'provider' | 'baseUrl' | 'model' | 'apiVersion'>) => void;
  onTest: () => void;
  testing: boolean;
}) {
  const theme = useLifeOSTheme();
  const { profile } = props;
  const usable = profile.enabled && Boolean(profile.baseUrl && profile.apiKey && profile.model);
  return (
    <View style={styles.providerColumn}>
      <Card style={styles.providerCard}>
        <View style={styles.switchRow}>
          <View style={styles.switchCopy}>
            <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>{props.title}</Text>
            <Text style={[styles.cardBody, { color: theme.colors.muted }]}>{props.subtitle}</Text>
          </View>
          <Switch
            accessibilityLabel={`${props.title} enabled`}
            value={profile.enabled}
            onValueChange={(enabled) => props.onChange({ enabled })}
            trackColor={{ false: theme.colors.line, true: theme.colors.mossSoft }}
            thumbColor={profile.enabled ? theme.colors.moss : theme.colors.muted}
          />
        </View>

        <View style={styles.providerChoices}>
          {providerPresets.map((preset) => {
            const selected = profile.provider === preset.patch.provider && profile.baseUrl === preset.patch.baseUrl && profile.model === preset.patch.model;
            return (
              <Pressable
                key={preset.label}
                accessibilityRole="button"
                onPress={() => props.onPreset(preset.patch)}
                style={[styles.presetChoice, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, selected && { backgroundColor: theme.colors.mossSoft, borderColor: theme.colors.moss }]}
              >
                <Text style={[styles.presetTitle, { color: theme.colors.ink }]}>{preset.label}</Text>
                <Text style={[styles.presetDetail, { color: theme.colors.muted }]}>{preset.detail}</Text>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.providerChoices}>
          {providerOptions.map((option) => {
            const selected = profile.provider === option.id;
            return (
              <Pressable
                key={option.id}
                accessibilityRole="button"
                onPress={() => props.onChoose(option.id)}
                style={[styles.providerChoice, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, selected && styles.providerChoiceSelected, selected && { backgroundColor: theme.colors.ink, borderColor: theme.colors.ink }]}
              >
                <Text style={[styles.providerChoiceText, { color: theme.colors.muted }, selected && styles.providerChoiceTextSelected, selected && { color: theme.colors.paper }]}>{option.label}</Text>
              </Pressable>
            );
          })}
        </View>

        {profile.enabled ? (
          <View style={styles.fields}>
            <Field label="Base URL" value={profile.baseUrl} placeholder="https://…" onChangeText={(baseUrl) => props.onChange({ baseUrl })} />
            <Field label={profile.provider === 'azure_openai' ? 'Deployment' : 'Model'} value={profile.model} placeholder="Model name" onChangeText={(model) => props.onChange({ model })} />
            {profile.provider !== 'openai_compatible' ? (
              <Field label="API version" value={profile.apiVersion} placeholder="API version" onChangeText={(apiVersion) => props.onChange({ apiVersion })} />
            ) : null}
            <Field label="API key" value={profile.apiKey} placeholder="Stored securely" secureTextEntry onChangeText={(apiKey) => props.onChange({ apiKey })} />
          </View>
        ) : null}

        <View style={styles.providerFooter}>
          <Text style={[styles.providerStatus, { color: theme.colors.muted }]}>{usable ? providerLabel(profile) : profile.enabled ? 'Finish required fields' : 'Off'}</Text>
          <Pressable
            accessibilityRole="button"
            disabled={!usable || props.testing}
            onPress={props.onTest}
            style={({ pressed }) => [styles.test, { backgroundColor: theme.colors.mossSoft }, (!usable || props.testing) && styles.disabled, pressed && styles.pressed]}
          >
            <Text style={[styles.testText, { color: theme.colors.moss }]}>{props.testing ? 'Testing…' : 'Test'}</Text>
          </Pressable>
        </View>
      </Card>
    </View>
  );
}

function SettingsStatusCard(props: {
  tone: 'moss' | 'blue' | 'amber' | 'plum';
  label: string;
  title: string;
  detail: string;
}) {
  const theme = useLifeOSTheme();
  return (
    <Card tone={props.tone} style={styles.statusCard}>
      <Text style={[styles.statusLabel, { color: theme.colors.muted }]}>{props.label}</Text>
      <Text style={[styles.statusTitle, { color: theme.colors.ink }]}>{props.title}</Text>
      <Text style={[styles.statusDetail, { color: theme.colors.muted }]}>{props.detail}</Text>
    </Card>
  );
}

function SettingsStatusRow({ label, value, ready }: { label: string; value: string; ready: boolean }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.quickStatusRow, { borderBottomColor: theme.colors.line }]}>
      <View style={[styles.quickStatusDot, { backgroundColor: ready ? theme.colors.moss : theme.colors.line }]} />
      <Text maxFontSizeMultiplier={1.2} style={[styles.quickStatusLabel, { color: theme.colors.ink }]}>{label}</Text>
      <Text maxFontSizeMultiplier={1.2} style={[styles.quickStatusValue, { color: theme.colors.muted }]}>{value}</Text>
    </View>
  );
}

function SettingsMenuRow({ label, value, active, onPress }: { label: string; value: string; active: boolean; onPress: () => void }) {
  const theme = useLifeOSTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      onPress={onPress}
      style={({ pressed }) => [styles.advancedMenuRow, { borderBottomColor: theme.colors.line }, active && { backgroundColor: theme.colors.mossSoft }, pressed && styles.pressed]}>
      <Text style={[styles.quickStatusLabel, { color: theme.colors.ink }]}>{label}</Text>
      <Text style={[styles.quickStatusValue, { color: theme.colors.muted }]}>{value}</Text>
      <Text style={[styles.advancedMenuArrow, { color: theme.colors.muted }]}>›</Text>
    </Pressable>
  );
}

function SourceCard(props: {
  title: string;
  detail: string;
  enabled: boolean;
  onEnabled: (enabled: boolean) => void;
  children: React.ReactNode;
}) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.providerColumn}>
      <Card style={styles.providerCard}>
        <View style={styles.switchRow}>
          <View style={styles.switchCopy}>
            <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>{props.title}</Text>
            <Text style={[styles.cardBody, { color: theme.colors.muted }]}>{props.detail}</Text>
          </View>
          <Switch
            accessibilityLabel={`${props.title} enabled`}
            value={props.enabled}
            onValueChange={props.onEnabled}
            trackColor={{ false: theme.colors.line, true: theme.colors.mossSoft }}
            thumbColor={props.enabled ? theme.colors.moss : theme.colors.muted}
          />
        </View>
        {props.enabled ? <View style={styles.fields}>{props.children}</View> : null}
      </Card>
    </View>
  );
}

function Field(props: {
  label: string;
  value: string;
  placeholder: string;
  onChangeText: (value: string) => void;
  secureTextEntry?: boolean;
}) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.field}>
      <Text style={[styles.fieldLabel, { color: theme.colors.ink }]}>{props.label}</Text>
      <TextInput
        accessibilityLabel={props.label}
        value={props.value}
        onChangeText={props.onChangeText}
        placeholder={props.placeholder}
        placeholderTextColor={theme.colors.muted}
        secureTextEntry={props.secureTextEntry}
        autoCapitalize="none"
        autoCorrect={false}
        style={[styles.input, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line, color: theme.colors.ink }]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  mobileHeader: { minHeight: 72, paddingTop: 10, paddingBottom: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  mobileTitle: { fontSize: 28, lineHeight: 33, fontWeight: '900', letterSpacing: -0.7 },
  mobileSubtitle: { fontSize: 12, lineHeight: 16, marginTop: 2 },
  quickStatus: { paddingVertical: 0, marginBottom: 12 },
  quickStatusRow: { minHeight: 52, borderBottomWidth: StyleSheet.hairlineWidth, flexDirection: 'row', alignItems: 'center', gap: 10 },
  quickStatusDot: { width: 8, height: 8, borderRadius: 4 },
  quickStatusLabel: { flex: 1, fontSize: 14, lineHeight: 18, fontWeight: '800' },
  quickStatusValue: { fontSize: 12, lineHeight: 16, fontWeight: '700' },
  advancedMenu: { padding: 0, overflow: 'hidden' },
  advancedMenuRow: { minHeight: 48, borderBottomWidth: StyleSheet.hairlineWidth, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 8 },
  advancedMenuArrow: { fontSize: 20, fontWeight: '400' },
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  principleCard: { padding: 22 },
  principleKicker: { color: colors.moss, fontSize: 10, fontWeight: '900', letterSpacing: 1.1 },
  principleTitle: { color: colors.ink, fontSize: 24, lineHeight: 29, fontWeight: '800', letterSpacing: -0.7, marginTop: 12 },
  principleBody: { color: colors.muted, fontSize: 13, lineHeight: 20, marginTop: 7, maxWidth: 680 },
  advancedCard: { padding: 0 },
  flow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 8, marginTop: 20 },
  arrow: { color: colors.muted, fontSize: 15 },
  providerGrid: { flexDirection: 'row', gap: 12, alignItems: 'stretch' },
  stack: { flexDirection: 'column' },
  statusGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 12 },
  statusCard: { flexGrow: 1, flexBasis: 220, minHeight: 132 },
  statusLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1.1, textTransform: 'uppercase' },
  statusTitle: { color: colors.ink, fontSize: 17, lineHeight: 22, fontWeight: '900', marginTop: 12 },
  statusDetail: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 6 },
  healthCard: { padding: 20, marginBottom: 12 },
  healthActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 18 },
  secondaryButton: { minHeight: 50, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center' },
  secondaryButtonText: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  providerColumn: { flex: 1, minWidth: 280 },
  providerCard: { height: '100%', padding: 20 },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 16 },
  switchCopy: { flex: 1 },
  cardTitle: { color: colors.ink, fontSize: 17, fontWeight: '800' },
  cardBody: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 4 },
  providerChoices: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginTop: 18 },
  providerChoice: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, paddingHorizontal: 10, paddingVertical: 8, backgroundColor: colors.paper },
  providerChoiceSelected: { backgroundColor: colors.ink, borderColor: colors.ink },
  providerChoiceText: { color: colors.muted, fontSize: 10, fontWeight: '800' },
  providerChoiceTextSelected: { color: '#FFF' },
  presetChoice: { flexGrow: 1, flexBasis: 118, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 10 },
  presetTitle: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  presetDetail: { color: colors.muted, fontSize: 10, lineHeight: 14, marginTop: 3 },
  fields: { gap: 12, marginTop: 18 },
  field: { gap: 6 },
  fieldLabel: { color: colors.ink, fontSize: 11, fontWeight: '800' },
  input: { minHeight: 48, borderWidth: 1, borderColor: colors.line, borderRadius: radius.sm, backgroundColor: '#FFF', color: colors.ink, fontSize: 13, paddingHorizontal: 13, paddingVertical: 11 },
  providerFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginTop: 18, paddingTop: 14, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  providerStatus: { color: colors.muted, fontSize: 11, fontWeight: '700', flex: 1 },
  test: { borderRadius: radius.pill, backgroundColor: colors.mossSoft, paddingHorizontal: 14, paddingVertical: 9 },
  testText: { color: colors.moss, fontSize: 11, fontWeight: '900' },
  connectorCard: { padding: 20 },
  configButton: { marginTop: 18, minHeight: 50, borderRadius: radius.pill, backgroundColor: colors.ink, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  configButtonText: { color: '#FFF', fontSize: 12, fontWeight: '900' },
  configButtonArrow: { color: '#FFF', fontSize: 18 },
  securityCard: { padding: 18 },
  securityTitle: { color: colors.blue, fontSize: 14, fontWeight: '800' },
  securityBody: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 5, maxWidth: 760 },
  notice: { color: colors.moss, fontSize: 13, fontWeight: '800', textAlign: 'center', marginTop: 14 },
  save: { minHeight: 52, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.ink, borderRadius: radius.pill, marginTop: 16, marginBottom: 28 },
  saveText: { color: '#FFF', fontSize: 13, fontWeight: '900' },
  disabled: { opacity: 0.4 },
  pressed: { opacity: 0.65 },
});
