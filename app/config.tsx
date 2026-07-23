import { ReactNode, useEffect, useMemo, useState } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import { useRouter } from 'expo-router';

import agentRegistry from '@/packages/domain-config/agents/registry.v1.json';
import catalog from '@/packages/domain-config/domain-catalog.v1.json';
import { Card, Page, PageHeader, Pill, SectionTitle, sharedStyles } from '@/src/components/ui';
import { DomainCatalogEntry, getDomainManifest, setActiveDomainOverride } from '@/src/domain/catalog';
import {
  LifeOSSettings,
  defaultLifeOSSettings,
  loadLifeOSSettings,
  saveLifeOSSettings,
} from '@/src/settings/lifeos-settings';
import { colors, radius } from '@/src/theme';

const workflows = [
  ['meal-plan-to-shopping', 'Meal plan to shopping'],
  ['receipt-to-kitchen', 'Receipt to kitchen'],
  ['weekly-food-reset', 'Weekly food reset'],
] as const;

const schemaFiles = [
  'record.v1.schema.json',
  'source-snapshot.v1.schema.json',
  'command.v1.schema.json',
  'action-event.v1.schema.json',
  'workflow.v1.schema.json',
  'agent-handoff.v1.schema.json',
  'undo.v1.schema.json',
] as const;

const catalogDomains = catalog.domains as unknown as DomainCatalogEntry[];

function toggle(values: string[], value: string, enabled: boolean) {
  return enabled ? Array.from(new Set([...values, value])) : values.filter((item) => item !== value);
}

export default function ConfigStudioScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 760;
  const [settings, setSettings] = useState<LifeOSSettings>(defaultLifeOSSettings);
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void loadLifeOSSettings().then(setSettings);
  }, []);

  const activeDomain = useMemo(
    () => catalogDomains.find((domain) => domain.id === settings.runtime.activeDomain) ?? catalogDomains[0],
    [settings.runtime.activeDomain],
  );
  const activeManifest = useMemo(
    () => getDomainManifest(catalogDomains, settings.runtime.activeDomain),
    [settings.runtime.activeDomain],
  );

  const updateRuntime = (patch: Partial<LifeOSSettings['runtime']>) => {
    setSettings((current) => ({ ...current, runtime: { ...current.runtime, ...patch } }));
  };

  const updateSurfaceConfig = <K extends keyof LifeOSSettings['runtime']['surfaceConfig']>(
    surface: K,
    patch: Partial<LifeOSSettings['runtime']['surfaceConfig'][K]>,
  ) => {
    setSettings((current) => ({
      ...current,
      runtime: {
        ...current.runtime,
        surfaceConfig: {
          ...current.runtime.surfaceConfig,
          [surface]: {
            ...current.runtime.surfaceConfig[surface],
            ...patch,
          },
        },
      },
    }));
  };

  const save = async () => {
    setNotice('');
    setSaving(true);
    try {
      const parsed = JSON.parse(settings.runtime.schemaOverrides);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        throw new Error('Advanced schema overrides must be a JSON object.');
      }
      const enabledDomains = settings.runtime.enabledDomains.includes(settings.runtime.activeDomain)
        ? settings.runtime.enabledDomains
        : [...settings.runtime.enabledDomains, settings.runtime.activeDomain];
      const saved = await saveLifeOSSettings({
        ...settings,
        runtime: { ...settings.runtime, enabledDomains },
      });
      setSettings(saved);
      setActiveDomainOverride(saved.runtime.activeDomain);
      setNotice('Config validated and activated.');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Config could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled">
        <View style={sharedStyles.content}>
          <View style={styles.contextBar}>
            <View>
              <Text style={styles.brand}>LIFEOS / CONFIG STUDIO</Text>
              <Text style={styles.context}>Packages, behavior and contracts</Text>
            </View>
            <Pill tone="moss">{activeDomain.label.toUpperCase()} ACTIVE</Pill>
          </View>

          <PageHeader
            eyebrow="Config is product"
            title="Shape LifeOS without rebuilding it."
            subtitle="Choose domains, tune skills, enable workflows and agents, adjust sync, and layer validated schema overrides. Your overrides survive app upgrades."
          />

          <SectionTitle title="Domains" />
          <View style={[styles.grid, compact && styles.stack]}>
            {catalogDomains.map((domain) => {
              const enabled = settings.runtime.enabledDomains.includes(domain.id);
              const active = settings.runtime.activeDomain === domain.id;
              return (
                <View key={domain.id} style={styles.column}>
                  <Card tone={active ? 'moss' : domain.id === 'health' ? 'plum' : 'blue'} style={styles.domainCard}>
                    <View style={styles.rowBetween}>
                      <View style={styles.domainGlyph}><Text style={styles.domainGlyphText}>{domain.label.slice(0, 1)}</Text></View>
                      <Switch
                        value={enabled}
                        onValueChange={(value) => updateRuntime({ enabledDomains: toggle(settings.runtime.enabledDomains, domain.id, value) })}
                        trackColor={{ false: colors.line, true: colors.mossSoft }}
                        thumbColor={enabled ? colors.moss : colors.muted}
                      />
                    </View>
                    <Text style={styles.cardTitle}>{domain.label}</Text>
                    <Text style={styles.cardBody}>{domain.summary}</Text>
                    <Pressable
                      accessibilityRole="button"
                      disabled={!enabled}
                      onPress={() => updateRuntime({ activeDomain: domain.id })}
                      style={[styles.select, active && styles.selectActive, !enabled && styles.disabled]}
                    >
                      <Text style={[styles.selectText, active && styles.selectTextActive]}>{active ? 'Active package' : 'Make active'}</Text>
                    </Pressable>
                  </Card>
                </View>
              );
            })}
          </View>

          {activeManifest ? (
            <>
              <SectionTitle title="Active package contract" />
              <Text style={styles.contractIntro}>Collections, relations, surfaces, provider fields and MCP contract are visible before you activate a package.</Text>
              <View style={[styles.contractGrid, compact && styles.stack]}>
                <Card style={styles.contractCard}>
                  <Text style={styles.contractLabel}>Collections</Text>
                  <Text style={styles.contractNumber}>{activeManifest.collections.length}</Text>
                  <ChipList values={activeManifest.collections} tone="moss" limit={14} />
                </Card>
                <Card style={styles.contractCard}>
                  <Text style={styles.contractLabel}>Surfaces</Text>
                  <Text style={styles.contractNumber}>{activeManifest.surfaces.length}</Text>
                  <ChipList values={activeManifest.surfaces.map((surface) => `${surface.label}: ${surface.collections.join(', ')}`)} tone="blue" limit={6} />
                </Card>
                <Card style={styles.contractCard}>
                  <Text style={styles.contractLabel}>Relations</Text>
                  <Text style={styles.contractNumber}>{activeManifest.relations.length}</Text>
                  <ChipList values={activeManifest.relations.map((relation) => `${relation.from} → ${relation.to} · ${relation.name}`)} tone="plum" limit={10} />
                </Card>
              </View>
              <View style={[styles.contractGrid, compact && styles.stack]}>
                <Card tone="blue" style={styles.contractCard}>
                  <Text style={styles.contractLabel}>Data homes</Text>
                  <ChipList values={activeManifest.data_homes} tone="blue" />
                  <Text style={styles.help}>Same package can run as app, Notion, Sheets, SQLite/Postgres, or MCP.</Text>
                </Card>
                <Card style={styles.contractCard}>
                  <Text style={styles.contractLabel}>MCP contract</Text>
                  <ChipList values={activeManifest.mcp.tools.map((tool) => `tool:${tool}`)} tone="moss" limit={8} />
                  <ChipList values={activeManifest.mcp.resources.map((resource) => `resource:${resource}`)} tone="amber" limit={8} />
                </Card>
                <Card tone="plum" style={styles.contractCard}>
                  <Text style={styles.contractLabel}>Provider fields</Text>
                  <ChipList values={[
                    ...(activeManifest.provider_template_fields?.required ?? []).map((field) => `required:${field}`),
                    ...(activeManifest.provider_template_fields?.rich_detail_json ?? []).map((field) => `detail:${field}`),
                    ...(activeManifest.provider_template_fields?.relations_json ?? []).map((field) => `relations:${field}`),
                  ]} tone="plum" limit={12} />
                </Card>
              </View>
            </>
          ) : null}

          <SectionTitle title="Skill instructions" />
          <Card style={styles.sectionCard}>
            <Text style={styles.lead}>Add your judgment above the bundled domain skill.</Text>
            <Text style={styles.help}>Examples: dietary rules, medical caution, preferred tone, household conventions, or plant-care philosophy.</Text>
            <View style={styles.fields}>
              {catalogDomains.filter((domain) => settings.runtime.enabledDomains.includes(domain.id)).map((domain) => (
                <View key={domain.id} style={styles.field}>
                  <View style={styles.fieldHeading}>
                    <Text style={styles.fieldLabel}>{domain.label} skill</Text>
                    <Text style={styles.filePath}>{domain.skill.replace('./skills/', '')}</Text>
                  </View>
                  <TextInput
                    value={settings.runtime.skillInstructions[domain.id] ?? ''}
                    onChangeText={(value) => updateRuntime({
                      skillInstructions: { ...settings.runtime.skillInstructions, [domain.id]: value },
                    })}
                    placeholder={`Add personal instructions for ${domain.label}…`}
                    placeholderTextColor={colors.muted}
                    multiline
                    style={[styles.input, styles.textarea]}
                  />
                </View>
              ))}
            </View>
          </Card>

          <View style={[styles.split, compact && styles.stack]}>
            <View style={styles.splitMain}>
              <SectionTitle title="Workflows" />
              <Card style={styles.listCard}>
                {workflows.map(([id, label]) => {
                  const enabled = settings.runtime.enabledWorkflows.includes(id);
                  return (
                    <ToggleRow
                      key={id}
                      title={label}
                      detail={`${id}.v1.json`}
                      value={enabled}
                      onValueChange={(value) => updateRuntime({ enabledWorkflows: toggle(settings.runtime.enabledWorkflows, id, value) })}
                    />
                  );
                })}
              </Card>
            </View>
            <View style={styles.splitMain}>
              <SectionTitle title="Agents" />
              <Card style={styles.listCard}>
                {agentRegistry.agents.map((agent) => {
                  const enabled = settings.runtime.enabledAgents.includes(agent.id);
                  return (
                    <ToggleRow
                      key={agent.id}
                      title={agent.id.replace(/_/g, ' ')}
                      detail={agent.role}
                      value={enabled}
                      onValueChange={(value) => updateRuntime({ enabledAgents: toggle(settings.runtime.enabledAgents, agent.id, value) })}
                    />
                  );
                })}
              </Card>
            </View>
          </View>

          <SectionTitle title="Behavior & appearance" />
          <Card style={styles.sectionCard}>
            <ToggleRow
              title="Automatic sync"
              detail="Pull enabled sources on your chosen cadence."
              value={settings.runtime.automaticSync}
              onValueChange={(automaticSync) => updateRuntime({ automaticSync })}
            />
            <ToggleRow
              title="Web search in Chat"
              detail="Allow configured models to use web search when supported."
              value={settings.runtime.webSearch}
              onValueChange={(webSearch) => updateRuntime({ webSearch })}
            />
            <View style={styles.inlineFields}>
              <ChoiceField
                label="Theme"
                value={settings.runtime.theme}
                choices={['system', 'light', 'dark']}
                onChange={(theme) => updateRuntime({ theme: theme as LifeOSSettings['runtime']['theme'] })}
              />
              <ChoiceField
                label="Density"
                value={settings.runtime.density}
                choices={['comfortable', 'compact']}
                onChange={(density) => updateRuntime({ density: density as LifeOSSettings['runtime']['density'] })}
              />
              <View style={styles.smallField}>
                <Text style={styles.fieldLabel}>Sync every minutes</Text>
                <TextInput
                  keyboardType="number-pad"
                  value={settings.runtime.syncMinutes}
                  onChangeText={(syncMinutes) => updateRuntime({ syncMinutes })}
                  style={styles.input}
                />
              </View>
            </View>
          </Card>

          <SectionTitle title="Screen composition" />
          <Card tone="moss" style={styles.sectionCard}>
            <Text style={styles.lead}>Every main screen gets runtime knobs.</Text>
            <Text style={styles.help}>These settings control section visibility and card counts without changing app code. Domain manifests still own the schema and collections.</Text>
            <View style={styles.surfaceGrid}>
              <SurfaceConfigCard title="Home">
                <ToggleRow title="Now card" detail="Show the primary daily card." value={settings.runtime.surfaceConfig.home.showNowCard} onValueChange={(showNowCard) => updateSurfaceConfig('home', { showNowCard })} />
                <ToggleRow title="Review queue" detail="Show items that need attention." value={settings.runtime.surfaceConfig.home.showReviewQueue} onValueChange={(showReviewQueue) => updateSurfaceConfig('home', { showReviewQueue })} />
                <Field label="Review cards" value={settings.runtime.surfaceConfig.home.reviewLimit} onChangeText={(reviewLimit) => updateSurfaceConfig('home', { reviewLimit })} />
                <ToggleRow title="Recent graph" detail="Show latest source-backed records." value={settings.runtime.surfaceConfig.home.showRecentGraph} onValueChange={(showRecentGraph) => updateSurfaceConfig('home', { showRecentGraph })} />
                <Field label="Recent graph cards" value={settings.runtime.surfaceConfig.home.recentLimit} onChangeText={(recentLimit) => updateSurfaceConfig('home', { recentLimit })} />
                <ToggleRow title="Life spaces section" detail="Show active/add-domain cards." value={settings.runtime.surfaceConfig.home.showLifeSpaces} onValueChange={(showLifeSpaces) => updateSurfaceConfig('home', { showLifeSpaces })} />
                <ToggleRow title="Source trust section" detail="Show Notion/Sheets/local trust cards." value={settings.runtime.surfaceConfig.home.showSourceTrust} onValueChange={(showSourceTrust) => updateSurfaceConfig('home', { showSourceTrust })} />
                <ToggleRow title="Control card" detail="Show Settings/Config entry point." value={settings.runtime.surfaceConfig.home.showControlCard} onValueChange={(showControlCard) => updateSurfaceConfig('home', { showControlCard })} />
              </SurfaceConfigCard>
              <SurfaceConfigCard title="Food">
                <ToggleRow title="Hero" detail="Show tonight/use-soon command area." value={settings.runtime.surfaceConfig.food.showHero} onValueChange={(showHero) => updateSurfaceConfig('food', { showHero })} />
                <ToggleRow title="View tabs" detail="Show Overview/Meals/Kitchen/Shopping." value={settings.runtime.surfaceConfig.food.showViewTabs} onValueChange={(showViewTabs) => updateSurfaceConfig('food', { showViewTabs })} />
                <ToggleRow title="Workspace board" detail="Show Meals/Kitchen/Shopping columns." value={settings.runtime.surfaceConfig.food.showWorkspace} onValueChange={(showWorkspace) => updateSurfaceConfig('food', { showWorkspace })} />
                <ToggleRow title="Attention section" detail="Show review cards." value={settings.runtime.surfaceConfig.food.showAttention} onValueChange={(showAttention) => updateSurfaceConfig('food', { showAttention })} />
                <ToggleRow title="Package card" detail="Show edit-package footer." value={settings.runtime.surfaceConfig.food.showPackageCard} onValueChange={(showPackageCard) => updateSurfaceConfig('food', { showPackageCard })} />
                <Field label="Cards per column" value={settings.runtime.surfaceConfig.food.columnLimit} onChangeText={(columnLimit) => updateSurfaceConfig('food', { columnLimit })} />
                <Field label="Attention cards" value={settings.runtime.surfaceConfig.food.attentionLimit} onChangeText={(attentionLimit) => updateSurfaceConfig('food', { attentionLimit })} />
              </SurfaceConfigCard>
              <SurfaceConfigCard title="Chat">
                <ToggleRow title="Thread rail" detail="Show conversation list." value={settings.runtime.surfaceConfig.chat.showThreads} onValueChange={(showThreads) => updateSurfaceConfig('chat', { showThreads })} />
                <ToggleRow title="Source strip" detail="Show source chips above chat." value={settings.runtime.surfaceConfig.chat.showSources} onValueChange={(showSources) => updateSurfaceConfig('chat', { showSources })} />
                <Field label="Source chips" value={settings.runtime.surfaceConfig.chat.sourceLimit} onChangeText={(sourceLimit) => updateSurfaceConfig('chat', { sourceLimit })} />
                <ToggleRow title="Prompt rail" detail="Show suggested prompts above composer." value={settings.runtime.surfaceConfig.chat.promptRail} onValueChange={(promptRail) => updateSurfaceConfig('chat', { promptRail })} />
                <ToggleRow title="Context card" detail="Show what the assistant can see." value={settings.runtime.surfaceConfig.chat.showContextCard} onValueChange={(showContextCard) => updateSurfaceConfig('chat', { showContextCard })} />
              </SurfaceConfigCard>
              <SurfaceConfigCard title="Record">
                <ToggleRow title="Hero" detail="Show title/status/action header." value={settings.runtime.surfaceConfig.record.showHero} onValueChange={(showHero) => updateSurfaceConfig('record', { showHero })} />
                <ToggleRow title="Nutrition" detail="Show nutrition cards." value={settings.runtime.surfaceConfig.record.showNutrition} onValueChange={(showNutrition) => updateSurfaceConfig('record', { showNutrition })} />
                <ToggleRow title="Ingredients" detail="Show availability board." value={settings.runtime.surfaceConfig.record.showIngredients} onValueChange={(showIngredients) => updateSurfaceConfig('record', { showIngredients })} />
                <ToggleRow title="Instructions" detail="Show cooking steps." value={settings.runtime.surfaceConfig.record.showInstructions} onValueChange={(showInstructions) => updateSurfaceConfig('record', { showInstructions })} />
                <ToggleRow title="History" detail="Show logs and variations." value={settings.runtime.surfaceConfig.record.showHistory} onValueChange={(showHistory) => updateSurfaceConfig('record', { showHistory })} />
                <ToggleRow title="Editable note" detail="Show note editor." value={settings.runtime.surfaceConfig.record.showEditableNote} onValueChange={(showEditableNote) => updateSurfaceConfig('record', { showEditableNote })} />
                <ToggleRow title="Properties" detail="Show source/status fields." value={settings.runtime.surfaceConfig.record.showProperties} onValueChange={(showProperties) => updateSurfaceConfig('record', { showProperties })} />
                <ToggleRow title="Relations" detail="Show connected record cards." value={settings.runtime.surfaceConfig.record.showRelations} onValueChange={(showRelations) => updateSurfaceConfig('record', { showRelations })} />
                <Field label="Nutrition cards" value={settings.runtime.surfaceConfig.record.nutritionLimit} onChangeText={(nutritionLimit) => updateSurfaceConfig('record', { nutritionLimit })} />
                <ToggleRow title="Provenance" detail="Show source/schema trust panel." value={settings.runtime.surfaceConfig.record.showProvenance} onValueChange={(showProvenance) => updateSurfaceConfig('record', { showProvenance })} />
              </SurfaceConfigCard>
            </View>
          </Card>

          <SectionTitle title="Schemas & advanced overrides" />
          <Card tone="blue" style={styles.sectionCard}>
            <View style={styles.schemaChips}>
              {schemaFiles.map((file) => <Pill key={file} tone="blue">{file.replace('.v1.schema.json', '')}</Pill>)}
            </View>
            <Text style={styles.help}>
              Bundled schemas stay versioned and safe. Put JSON Merge Patch-style overrides here; invalid JSON cannot activate.
            </Text>
            <TextInput
              value={settings.runtime.schemaOverrides}
              onChangeText={(schemaOverrides) => updateRuntime({ schemaOverrides })}
              multiline
              autoCapitalize="none"
              autoCorrect={false}
              style={[styles.input, styles.code]}
            />
          </Card>

          {notice ? <Text accessibilityLiveRegion="polite" style={styles.notice}>{notice}</Text> : null}
          <View style={styles.actions}>
            <Pressable
              accessibilityRole="button"
              disabled={saving}
              onPress={() => void save()}
              style={({ pressed }) => [styles.save, saving && styles.disabled, pressed && styles.pressed]}
            >
              <Text style={styles.saveText}>{saving ? 'Validating…' : 'Save & activate'}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={() => router.replace('/')} style={({ pressed }) => [styles.open, pressed && styles.pressed]}>
              <Text style={styles.openText}>Open active domain</Text>
            </Pressable>
          </View>
        </View>
      </ScrollView>
    </Page>
  );
}

function ToggleRow(props: { title: string; detail: string; value: boolean; onValueChange: (value: boolean) => void }) {
  return (
    <View style={styles.toggleRow}>
      <View style={styles.toggleCopy}>
        <Text style={styles.toggleTitle}>{props.title}</Text>
        <Text style={styles.toggleDetail}>{props.detail}</Text>
      </View>
      <Switch
        value={props.value}
        onValueChange={props.onValueChange}
        trackColor={{ false: colors.line, true: colors.mossSoft }}
        thumbColor={props.value ? colors.moss : colors.muted}
      />
    </View>
  );
}

function ChoiceField(props: { label: string; value: string; choices: string[]; onChange: (value: string) => void }) {
  return (
    <View style={styles.choiceField}>
      <Text style={styles.fieldLabel}>{props.label}</Text>
      <View style={styles.choices}>
        {props.choices.map((choice) => {
          const active = props.value === choice;
          return (
            <Pressable key={choice} onPress={() => props.onChange(choice)} style={[styles.choice, active && styles.choiceActive]}>
              <Text style={[styles.choiceText, active && styles.choiceTextActive]}>{choice}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function SurfaceConfigCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.surfaceCard}>
      <Text style={styles.surfaceTitle}>{title}</Text>
      <View style={styles.surfaceBody}>{children}</View>
    </View>
  );
}

function Field(props: { label: string; value: string; onChangeText: (value: string) => void }) {
  return (
    <View style={styles.smallField}>
      <Text style={styles.fieldLabel}>{props.label}</Text>
      <TextInput
        keyboardType="number-pad"
        value={props.value}
        onChangeText={props.onChangeText}
        style={styles.input}
      />
    </View>
  );
}

function ChipList(props: { values: string[]; tone: 'moss' | 'blue' | 'amber' | 'plum'; limit?: number }) {
  const visible = props.values.slice(0, props.limit ?? props.values.length);
  const hidden = props.values.length - visible.length;
  return (
    <View style={styles.contractChips}>
      {visible.map((value) => <Pill key={value} tone={props.tone}>{value}</Pill>)}
      {hidden > 0 ? <Pill tone="neutral">+{hidden}</Pill> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.blue, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  grid: { flexDirection: 'row', gap: 12 },
  contractGrid: { flexDirection: 'row', gap: 12, marginBottom: 12 },
  contractIntro: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: -4, marginBottom: 12 },
  contractCard: { flex: 1, minWidth: 240, minHeight: 180 },
  contractLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase' },
  contractNumber: { color: colors.ink, fontSize: 34, lineHeight: 40, fontWeight: '900', letterSpacing: -1, marginTop: 10, marginBottom: 12 },
  contractChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 10 },
  stack: { flexDirection: 'column' },
  column: { flex: 1, minWidth: 220 },
  domainCard: { minHeight: 255, padding: 20 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  domainGlyph: { width: 42, height: 42, borderRadius: 14, backgroundColor: colors.ink, alignItems: 'center', justifyContent: 'center' },
  domainGlyphText: { color: '#FFF', fontSize: 18, fontWeight: '900' },
  cardTitle: { color: colors.ink, fontSize: 19, fontWeight: '800', marginTop: 18 },
  cardBody: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 6 },
  select: { marginTop: 'auto', borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, alignItems: 'center', padding: 10 },
  selectActive: { backgroundColor: colors.ink, borderColor: colors.ink },
  selectText: { color: colors.ink, fontSize: 11, fontWeight: '900' },
  selectTextActive: { color: '#FFF' },
  sectionCard: { padding: 20 },
  lead: { color: colors.ink, fontSize: 16, fontWeight: '800' },
  help: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 5 },
  fields: { gap: 18, marginTop: 18 },
  field: { gap: 7 },
  fieldHeading: { flexDirection: 'row', justifyContent: 'space-between', gap: 12 },
  fieldLabel: { color: colors.ink, fontSize: 11, fontWeight: '900' },
  filePath: { color: colors.blue, fontSize: 10, fontFamily: 'monospace' },
  input: { minHeight: 46, borderWidth: 1, borderColor: colors.line, borderRadius: radius.sm, backgroundColor: '#FFF', color: colors.ink, paddingHorizontal: 12, paddingVertical: 10, fontSize: 13 },
  textarea: { minHeight: 100, textAlignVertical: 'top' },
  split: { flexDirection: 'row', gap: 12 },
  splitMain: { flex: 1, minWidth: 280 },
  listCard: { paddingVertical: 5 },
  toggleRow: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 14, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  toggleCopy: { flex: 1 },
  toggleTitle: { color: colors.ink, fontSize: 13, fontWeight: '800', textTransform: 'capitalize' },
  toggleDetail: { color: colors.muted, fontSize: 11, lineHeight: 16, marginTop: 3 },
  inlineFields: { flexDirection: 'row', flexWrap: 'wrap', gap: 18, marginTop: 18 },
  surfaceGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginTop: 18 },
  surfaceCard: { flexGrow: 1, flexBasis: 240, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 14 },
  surfaceTitle: { color: colors.ink, fontSize: 16, fontWeight: '900' },
  surfaceBody: { gap: 10, marginTop: 12 },
  choiceField: { gap: 8 },
  smallField: { gap: 8, minWidth: 150 },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  choice: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, paddingHorizontal: 11, paddingVertical: 8 },
  choiceActive: { backgroundColor: colors.ink, borderColor: colors.ink },
  choiceText: { color: colors.muted, fontSize: 10, fontWeight: '800', textTransform: 'capitalize' },
  choiceTextActive: { color: '#FFF' },
  schemaChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  code: { minHeight: 180, marginTop: 16, fontFamily: 'monospace', textAlignVertical: 'top' },
  notice: { color: colors.moss, fontSize: 13, fontWeight: '800', textAlign: 'center', marginTop: 16 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 16, marginBottom: 28 },
  save: { flexGrow: 1, minHeight: 52, borderRadius: radius.pill, backgroundColor: colors.ink, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 20 },
  saveText: { color: '#FFF', fontSize: 12, fontWeight: '900' },
  open: { minHeight: 52, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 20 },
  openText: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  disabled: { opacity: 0.4 },
  pressed: { opacity: 0.65 },
});
