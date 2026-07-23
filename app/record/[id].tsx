import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';

import { ActionButton, Card, Page, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { colors, radius, useLifeOSTheme } from '@/src/theme';
import { useLifeOSDatabase } from '@/src/db/provider';
import { getDomainRecord, getDomainRecordCanonical } from '@/src/domain/queries';
import { loadCatalog } from '@/src/domain/catalog';
import { CanonicalRecord } from '@/src/domain/runtime';
import { getRecordsByIds, upsertRecord } from '@/src/db/records';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { mergeVisualIdentity, visualGlyph } from '@/src/domain/visual-identity';

type FoodDetail = {
  kind: 'meal' | 'recipe' | 'inventory' | 'shopping' | 'generic';
  nutrition: Array<[string, string]>;
  ingredients: Array<{ name: string; amount: string; state: 'available' | 'needed' | 'shopping' | 'previous' }>;
  instructions: string[];
  logs: Array<[string, string]>;
  variations: string[];
};

const ingredientStates = new Set(['available', 'needed', 'shopping', 'previous']);

function countSetting(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const RECORD_SECTIONS = ['hero', 'nutrition', 'ingredients', 'instructions', 'history', 'editableNote', 'properties', 'relations', 'provenance'] as const;
type RecordSection = typeof RECORD_SECTIONS[number];
const MAIN_RECORD_SECTIONS = new Set<RecordSection>(['nutrition', 'ingredients', 'instructions', 'history', 'editableNote']);
const SIDE_RECORD_SECTIONS = new Set<RecordSection>(['properties', 'relations', 'provenance']);

function orderedRecordSections(value: string) {
  const allowed = new Set<string>(RECORD_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is RecordSection => allowed.has(section));
  const missing = RECORD_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

function orderedRecordSubset(value: string, subset: Set<RecordSection>) {
  return orderedRecordSections(value).filter((section) => subset.has(section));
}

function fallbackCanonicalFromView(
  view: NonNullable<Awaited<ReturnType<typeof getDomainRecord>>>,
  domainId: string
): CanonicalRecord {
  const sourceParts = view.source.split(' · ');
  const provider = (sourceParts[0] ?? 'notion').toLowerCase().replace(' ', '_') || 'notion';
  return {
    id: view.id,
    domain: domainId,
    collection: view.collection ?? 'sample',
    title: view.title,
    properties: {
      status: view.status,
      tone: view.tone,
      meta: view.meta,
      body: view.body,
      source: view.source,
    },
    relations: [],
    source: {
      provider: provider as CanonicalRecord['source']['provider'],
      external_id: sourceParts[1] ? sourceParts.slice(1).join(' · ') : view.source,
      url: null,
      observed_at: new Date().toISOString(),
      content_hash: null,
    },
    archived_at: null,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  };
}

function toTone(value: unknown) {
  if (value === 'moss' || value === 'amber' || value === 'plum' || value === 'blue' || value === 'neutral') {
    return value;
  }
  return 'neutral';
}

function asText(value: unknown, fallback = '') {
  return typeof value === 'string' && value.trim() ? value : fallback;
}

function isTupleArray(value: unknown): value is Array<[string, string]> {
  return Array.isArray(value)
    && value.every((item) => Array.isArray(item) && item.length === 2 && typeof item[0] === 'string' && typeof item[1] === 'string');
}

function isFoodDetail(value: unknown): value is Omit<FoodDetail, 'kind'> {
  if (!value || typeof value !== 'object') return false;
  const detail = value as Record<string, unknown>;
  return isTupleArray(detail.nutrition)
    && Array.isArray(detail.ingredients)
    && detail.ingredients.every((item) => {
      if (!item || typeof item !== 'object') return false;
      const ingredient = item as Record<string, unknown>;
      return typeof ingredient.name === 'string'
        && typeof ingredient.amount === 'string'
        && typeof ingredient.state === 'string'
        && ingredientStates.has(ingredient.state);
    })
    && Array.isArray(detail.instructions)
    && detail.instructions.every((item) => typeof item === 'string')
    && isTupleArray(detail.logs)
    && Array.isArray(detail.variations)
    && detail.variations.every((item) => typeof item === 'string');
}

function collectionKind(collection: string): FoodDetail['kind'] {
  if (collection === 'recipe') return 'recipe';
  if (collection === 'meal_plan' || collection === 'meal_log') return 'meal';
  if (collection === 'inventory' || collection === 'ingredient' || collection === 'purchase_line') return 'inventory';
  if (collection === 'shopping_item' || collection === 'purchase') return 'shopping';
  return 'generic';
}

function getFoodDetail(record: CanonicalRecord): FoodDetail {
  const detail = record.properties.food_detail;
  if (isFoodDetail(detail)) {
    return {
      kind: collectionKind(record.collection),
      nutrition: detail.nutrition,
      ingredients: detail.ingredients,
      instructions: detail.instructions,
      logs: detail.logs,
      variations: detail.variations,
    };
  }

  return inferFoodDetail(record);
}

function hasStructuredFoodDetail(record: CanonicalRecord): boolean {
  return isFoodDetail(record.properties.food_detail);
}

function inferFoodDetail(record: CanonicalRecord): FoodDetail {
  const text = `${record.title} ${record.collection} ${asText(record.properties.meta)} ${asText(record.properties.body)}`.toLowerCase();
  const isDal = text.includes('dal') || text.includes('rice');
  const isTandoori = text.includes('tandoori') || text.includes('chicken');
  const isYogurt = text.includes('yogurt');
  const isShopping = record.collection === 'shopping_item' || text.includes('shopping') || text.includes('to buy');

  if (isDal) {
    return {
      kind: 'meal',
      nutrition: [
        ['Calories', '~520 kcal'],
        ['Protein', '~24 g'],
        ['Fiber', '~13 g'],
        ['Carbs', '~82 g'],
        ['Fat', '~10 g'],
        ['Sodium', 'Depends on salt/stock'],
      ],
      ingredients: [
        { name: 'Moong dal', amount: '1 cup dry', state: 'available' },
        { name: 'Rice', amount: '1.5 cups cooked', state: 'available' },
        { name: 'Frozen ginger', amount: '1 tbsp', state: 'available' },
        { name: 'Baby spinach', amount: '1 bag', state: 'shopping' },
        { name: 'Tomato or lemon', amount: 'optional acid', state: 'previous' },
      ],
      instructions: [
        'Rinse moong dal until water runs mostly clear.',
        'Simmer dal with turmeric, ginger and salt until soft.',
        'Cook rice separately or use leftover rice.',
        'Fold spinach into dal at the end so it stays green.',
        'Finish with lemon, ghee or tempering if available.',
        'Batch extra dal for Friday lunch.',
      ],
      logs: [
        ['Planned', 'Thursday dinner'],
        ['Previous note', 'Batch enough for Friday lunch'],
        ['Shopping link', 'Baby spinach needed'],
      ],
      variations: [
        'Add yogurt raita if dinner needs more protein.',
        'Use frozen spinach if fresh spinach is not bought.',
        'Turn leftovers into khichdi with extra water and rice.',
      ],
    };
  }

  if (isTandoori) {
    return {
      kind: 'recipe',
      nutrition: [
        ['Calories', '~610 kcal'],
        ['Protein', '~46 g'],
        ['Carbs', '~28 g'],
        ['Fat', '~34 g'],
        ['Fiber', '~7 g'],
        ['Serving', '1 of 4'],
      ],
      ingredients: [
        { name: 'Chicken thighs', amount: '1.5 lb', state: 'needed' },
        { name: 'Greek yogurt', amount: '1 cup marinade', state: 'available' },
        { name: 'Lemon', amount: '1', state: 'needed' },
        { name: 'Garam masala', amount: '2 tsp', state: 'previous' },
        { name: 'Broccoli', amount: '1 head', state: 'needed' },
      ],
      instructions: [
        'Mix yogurt, lemon, garam masala, salt and oil.',
        'Coat chicken and rest 20 minutes if time allows.',
        'Spread chicken and broccoli on a sheet pan.',
        'Roast at 425 F until chicken is cooked through.',
        'Rest 5 minutes before serving.',
      ],
      logs: [
        ['Last cooked', 'Use yogurt before expiry'],
        ['Planned', 'Tonight'],
        ['Serves', 'Four'],
      ],
      variations: [
        'Swap broccoli for cauliflower.',
        'Serve with rice bowl base.',
        'Use tofu/paneer with the same marinade.',
      ],
    };
  }

  if (isYogurt) {
    return {
      kind: 'inventory',
      nutrition: [
        ['Calories', '~140 kcal'],
        ['Protein', '~18 g'],
        ['Carbs', '~7 g'],
        ['Fat', '~4 g'],
        ['Serving', '170 g'],
      ],
      ingredients: [
        { name: 'Greek yogurt', amount: '2 tubs', state: 'available' },
        { name: 'Blueberries', amount: 'for bowls', state: 'previous' },
        { name: 'Chicken marinade', amount: '1 cup needed', state: 'available' },
      ],
      instructions: [
        'Use first in breakfast bowls or tandoori marinade.',
        'Check expiry before planning weekend meals.',
      ],
      logs: [
        ['Bought', 'Whole Foods'],
        ['Use by', '2 days'],
        ['Linked recipe', 'Sheet-pan tandoori chicken'],
      ],
      variations: ['Raita', 'Smoothie bowl', 'Protein pancake topping'],
    };
  }

  if (isShopping) {
    return {
      kind: 'shopping',
      nutrition: [
        ['Calories', '~23 kcal'],
        ['Protein', '~3 g'],
        ['Fiber', '~2 g'],
        ['Iron', 'High'],
      ],
      ingredients: [
        { name: record.title, amount: '1 bag', state: 'shopping' },
        { name: 'Green dal', amount: 'linked meal', state: 'needed' },
        { name: 'Breakfast omelettes', amount: 'planned use', state: 'needed' },
      ],
      instructions: [
        'Buy one fresh bag.',
        'Put away in produce drawer.',
        'Use first for green dal, then omelettes.',
      ],
      logs: [
        ['Reason', 'Needed for green dal and breakfast omelettes'],
        ['State', 'To buy'],
      ],
      variations: ['Frozen spinach backup', 'Baby kale if spinach unavailable'],
    };
  }

  return {
    kind: 'generic',
    nutrition: [['Nutrition', 'Not captured yet']],
    ingredients: [],
    instructions: [],
    logs: [['Created', new Date(record.created_at).toLocaleDateString()]],
    variations: [],
  };
}

function stateTone(state: FoodDetail['ingredients'][number]['state']) {
  if (state === 'available') return 'moss';
  if (state === 'shopping') return 'blue';
  if (state === 'needed') return 'amber';
  return 'plum';
}

function parseNutritionNumber(value: string) {
  const match = value.match(/(~)?\s*(\d+(?:\.\d+)?)\s*([a-zA-Z%]+)?/);
  if (!match) return null;
  return {
    approximate: Boolean(match[1]) || value.includes('~'),
    amount: Number.parseFloat(match[2]),
    unit: match[3] ?? '',
  };
}

function scaledNutritionValue(label: string, value: string, servings: number) {
  const parsed = parseNutritionNumber(value);
  if (!parsed || /serving/i.test(label)) return value;
  const next = parsed.amount * servings;
  const rounded = next >= 100 ? Math.round(next) : Math.round(next * 10) / 10;
  return `${parsed.approximate ? '~' : ''}${rounded}${parsed.unit ? ` ${parsed.unit}` : ''}`;
}

function macroColor(label: string): 'moss' | 'amber' | 'blue' | 'plum' {
  const key = label.toLowerCase();
  if (key.includes('protein')) return 'moss';
  if (key.includes('carb') || key.includes('fiber')) return 'blue';
  if (key.includes('fat')) return 'amber';
  return 'plum';
}

export default function RecordScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 860;
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();
  const settings = useLifeOSSettingsSnapshot();
  const visualIdentity = mergeVisualIdentity(catalog.activeManifest, settings.runtime.visualIdentityOverrides);
  const theme = useLifeOSTheme();

  const [record, setRecord] = useState<CanonicalRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [saved, setSaved] = useState({ title: '', body: '' });
  const [propertiesDraft, setPropertiesDraft] = useState('{}');
  const [savedPropertiesDraft, setSavedPropertiesDraft] = useState('{}');
  const [linkedRecords, setLinkedRecords] = useState<Record<string, CanonicalRecord>>({});
  const [notice, setNotice] = useState('');
  const [actionPanelOpen, setActionPanelOpen] = useState(false);
  const [servingMultiplier, setServingMultiplier] = useState(1);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);

    const load = async () => {
      let canonical: CanonicalRecord | null = null;
      if (db) {
        canonical = await getDomainRecordCanonical(db, id);
      } else {
        const fallback = await getDomainRecord(null, id);
        if (fallback) {
          canonical = fallbackCanonicalFromView(fallback, catalog.activeDomainId);
        }
      }

      if (cancelled) {
        return;
      }
      if (!canonical) {
        setNotFound(true);
        setLoading(false);
        return;
      }

      setRecord(canonical);
      if (db && canonical.relations.length) {
        const linked = await getRecordsByIds(db, canonical.relations.map((relation) => relation.target_id));
        if (!cancelled) {
          setLinkedRecords(Object.fromEntries(linked.map((item) => [item.id, item])));
        }
      } else if (!cancelled) {
        setLinkedRecords({});
      }
      setTitle(canonical.title);
      setBody(String(canonical.properties.body ?? ''));
      setSaved({ title: canonical.title, body: String(canonical.properties.body ?? '') });
      const nextPropertiesDraft = JSON.stringify(canonical.properties ?? {}, null, 2);
      setPropertiesDraft(nextPropertiesDraft);
      setSavedPropertiesDraft(nextPropertiesDraft);
      setLoading(false);
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [db, id, catalog]);

  const dirty = title !== saved.title || body !== saved.body;
  const propertiesDirty = propertiesDraft !== savedPropertiesDraft;
  const meta = record ? String(record.properties.meta ?? '') : '';
  const sourceLabel = record
    ? `${record.source.provider}${record.source.external_id ? ` · ${record.source.external_id}` : ''}`
    : '';
  const updatedAt = record ? new Date(record.updated_at).toLocaleString() : '';
  const tone = record ? toTone(record.properties.tone) : 'neutral';
  const status = record ? String(record.properties.status ?? 'Active') : 'Active';
  const relations = record?.relations ?? [];
  const foodDetail = record ? getFoodDetail(record) : null;
  const structuredFoodDetail = record ? hasStructuredFoodDetail(record) : false;
  const ingredientsByState = foodDetail
    ? {
        available: foodDetail.ingredients.filter((ingredient) => ingredient.state === 'available'),
        needed: foodDetail.ingredients.filter((ingredient) => ingredient.state === 'needed'),
        shopping: foodDetail.ingredients.filter((ingredient) => ingredient.state === 'shopping'),
        previous: foodDetail.ingredients.filter((ingredient) => ingredient.state === 'previous'),
      }
    : { available: [], needed: [], shopping: [], previous: [] };
  const recordConfig = settings.runtime.surfaceConfig.record;
  const nutritionLimit = countSetting(recordConfig.nutritionLimit, 6);
  const primaryNutrition = foodDetail?.nutrition.slice(0, Math.min(4, nutritionLimit)) ?? [];
  const openIngredientCount = ingredientsByState.needed.length + ingredientsByState.shopping.length;
  const availabilitySummary = foodDetail
    ? `${ingredientsByState.available.length} available · ${openIngredientCount} to resolve`
    : '';
  const primaryNutritionSummary = primaryNutrition.slice(0, 2);
  const nutritionRows = foodDetail?.nutrition.slice(0, nutritionLimit).map(([label, value]) => ({
    label,
    value,
    scaledValue: scaledNutritionValue(label, value, servingMultiplier),
    parsed: parseNutritionNumber(value),
  })) ?? [];
  const macroRows = nutritionRows.filter((row) => /protein|carb|fat|fiber/i.test(row.label) && row.parsed);
  const macroMax = Math.max(...macroRows.map((row) => (row.parsed?.amount ?? 0) * servingMultiplier), 1);
  const caloriesRow = nutritionRows.find((row) => /calorie|kcal/i.test(row.label));
  const servingLabel = nutritionRows.find((row) => /serving/i.test(row.label))?.value ?? '1 serving';
  const nutritionSourceLabel = structuredFoodDetail
    ? `Saved food_detail from ${sourceLabel || 'source record'}`
    : 'Inferred estimate from title/body until Notion, Sheets or the device syncs source detail';
  const nextStep = foodDetail?.instructions[0] ?? 'Add instructions in the source note.';
  const missingSummary = [...ingredientsByState.needed, ...ingredientsByState.shopping]
    .slice(0, 3)
    .map((ingredient) => ingredient.name)
    .join(', ') || 'nothing blocking';
  const readySummary = ingredientsByState.available.slice(0, 3).map((ingredient) => ingredient.name).join(', ') || 'not captured';

  const handleSave = async () => {
    if (!record || !db) {
      setSaved({ title, body });
      return;
    }

    await upsertRecord(
      db,
      loadCatalog().activeManifest,
      {
        id: record.id,
        title,
        collection: record.collection,
        properties: {
          ...record.properties,
          body,
          title,
          status,
          meta,
        },
        created_at: record.created_at,
        updated_at: new Date().toISOString(),
        source: record.source,
        archived_at: record.archived_at,
        relations: record.relations,
      }
    );

    const next = await getDomainRecordCanonical(db, record.id);
    if (next) {
      setRecord(next);
    }
    setSaved({ title, body });
  };

  const handleUndo = () => {
    setTitle(saved.title);
    setBody(saved.body);
  };

  const handleSaveProperties = async () => {
    if (!record || !db) {
      setSavedPropertiesDraft(propertiesDraft);
      setNotice('Saved locally in this view. Open Settings to connect a writable source.');
      return;
    }

    let parsed: Record<string, unknown>;
    try {
      const value = JSON.parse(propertiesDraft) as unknown;
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw new Error('Properties must be a JSON object.');
      }
      parsed = value as Record<string, unknown>;
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Properties JSON is invalid.');
      return;
    }

    await upsertRecord(db, loadCatalog().activeManifest, {
      ...record,
      title: String(parsed.title ?? record.title),
      properties: parsed,
      updated_at: new Date().toISOString(),
    });
    const refreshed = await getDomainRecordCanonical(db, record.id);
    const nextRecord = refreshed ?? { ...record, properties: parsed };
    const nextDraft = JSON.stringify(nextRecord.properties ?? {}, null, 2);
    setRecord(nextRecord);
    setPropertiesDraft(nextDraft);
    setSavedPropertiesDraft(nextDraft);
    setTitle(nextRecord.title);
    setBody(String(nextRecord.properties.body ?? ''));
    setSaved({ title: nextRecord.title, body: String(nextRecord.properties.body ?? '') });
    setNotice('Saved record properties.');
  };

  const persistRecordPatch = async (patch: Partial<CanonicalRecord>) => {
    if (!record || !db) {
      setNotice('Saved locally in this view. Open Settings to connect a writable source.');
      return;
    }

    const nextRecord: CanonicalRecord = {
      ...record,
      ...patch,
      properties: {
        ...record.properties,
        ...(patch.properties ?? {}),
      },
      updated_at: new Date().toISOString(),
    };

    await upsertRecord(db, loadCatalog().activeManifest, nextRecord);
    const refreshed = await getDomainRecordCanonical(db, record.id);
    setRecord(refreshed ?? nextRecord);
    setNotice('Updated this Food record.');
  };

  const handlePrimaryFoodAction = async () => {
    if (!record || !foodDetail) return;
    setActionPanelOpen((open) => !open);
  };

  const appendFoodLog = async (label: string, value: string, nextStatus?: string) => {
    if (!record || !foodDetail) return;
    const stamped = new Date().toLocaleString();
    const nextDetail = {
      ...foodDetail,
      logs: [
        [label, `${value} · ${stamped}`] as [string, string],
        ...foodDetail.logs.filter(([existing]) => existing !== label),
      ],
    };
    await persistRecordPatch({
      properties: {
        food_detail: nextDetail,
        status: nextStatus ?? (foodDetail.kind === 'shopping' ? 'Review' : 'Logged'),
      },
    });
    setActionPanelOpen(false);
  };

  const noteUnsupportedAction = (message: string) => {
    setNotice(message);
    setActionPanelOpen(false);
  };

  if (loading) {
    return (
      <Page>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            <Text style={[styles.loading, { color: theme.colors.muted }]}>Loading record…</Text>
          </View>
        </ScrollView>
      </Page>
    );
  }

  if (notFound || !record) {
    return (
      <Page>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            <Text style={[styles.emptyTitle, { color: theme.colors.ink }]}>Record not found</Text>
            <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>This item is not available in the current life space.</Text>
            <Pressable onPress={() => router.back()} style={styles.close}>
              <Text style={[styles.closeText, { color: theme.colors.muted }]}>Close</Text>
            </Pressable>
          </View>
        </ScrollView>
      </Page>
    );
  }

  const recordSections = orderedRecordSections(recordConfig.sectionOrder);
  const mainRecordSections = orderedRecordSubset(recordConfig.mainSectionOrder || recordConfig.sectionOrder, MAIN_RECORD_SECTIONS);
  const sideRecordSections = orderedRecordSubset(recordConfig.sideSectionOrder || recordConfig.sectionOrder, SIDE_RECORD_SECTIONS);
  const pageLabel = record.collection === 'meal_plan'
    ? 'Meal plan'
    : record.collection === 'recipe'
      ? 'Recipe'
      : record.collection === 'inventory' || record.collection === 'inventory_lot'
        ? 'Kitchen item'
        : record.collection === 'shopping_item' || record.collection === 'shopping_demand'
          ? 'Shopping item'
          : 'Food page';

  const renderHeroSection = () => (
    recordConfig.showHero ? (
      <Card key="hero" tone={tone === 'neutral' ? undefined : tone} style={styles.hero}>
        <View style={styles.statusRow}>
          <Pill tone={tone}>{status}</Pill>
          <Text style={[styles.sync, { color: theme.colors.muted }]}>Updated {updatedAt}</Text>
        </View>
        <TextInput accessibilityLabel="Record title" value={title} onChangeText={setTitle} style={[styles.title, { color: theme.colors.ink }]} multiline />
        <Text style={[styles.meta, { color: theme.colors.muted }]}>{pageLabel} · {meta}</Text>
        <Text style={[styles.heroBody, { color: theme.colors.ink }]}>{body || 'No note yet.'}</Text>
        {foodDetail ? (
          <View style={styles.heroEvidence}>
            {primaryNutritionSummary.map(([label, value]) => (
              <View key={`hero-${label}`} style={[styles.heroEvidenceCell, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
                <Text style={[styles.heroEvidenceValue, { color: theme.colors.ink }]}>{value}</Text>
                <Text style={[styles.heroEvidenceLabel, { color: theme.colors.muted }]}>{label}</Text>
              </View>
            ))}
            <View style={[styles.heroEvidenceCell, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
              <Text style={[styles.heroEvidenceValue, { color: theme.colors.ink }]}>{availabilitySummary}</Text>
              <Text style={[styles.heroEvidenceLabel, { color: theme.colors.muted }]}>Availability</Text>
            </View>
            <View style={[styles.heroEvidenceCell, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
              <Text style={[styles.heroEvidenceValue, { color: theme.colors.ink }]}>{foodDetail.instructions.length} steps · {foodDetail.logs.length} logs</Text>
              <Text style={[styles.heroEvidenceLabel, { color: theme.colors.muted }]}>History</Text>
            </View>
          </View>
        ) : null}
        <View style={styles.quickActions}>
          <ActionButton label="Ask about this" quiet onPress={() => router.push('/chat')} />
          <ActionButton label={foodDetail?.kind === 'recipe' || foodDetail?.kind === 'meal' ? 'Cook / log' : 'Use / update'} quiet onPress={() => { void handlePrimaryFoodAction(); }} />
          <ActionButton label="Add missing" quiet onPress={() => router.push('/(tabs)/food')} />
        </View>
        {actionPanelOpen && foodDetail ? (
          <Card style={styles.actionPanel}>
            <View style={styles.actionPanelHead}>
              <Text style={[styles.actionTitle, { color: theme.colors.ink }]}>What happened?</Text>
              <Pressable accessibilityRole="button" onPress={() => setActionPanelOpen(false)} hitSlop={8}><Text style={[styles.actionClose, { color: theme.colors.muted }]}>Close</Text></Pressable>
            </View>
            <View style={styles.actionGrid}>
              <ActionButton label={foodDetail.kind === 'shopping' ? 'Bought it' : 'Cooked this'} quiet onPress={() => { void appendFoodLog(foodDetail.kind === 'shopping' ? 'Bought in app' : 'Cooked in app', foodDetail.kind === 'shopping' ? 'Moved from shopping to kitchen memory' : 'Completed meal/recipe', foodDetail.kind === 'shopping' ? 'Bought' : 'Cooked'); }} />
              <ActionButton label="Skipped" quiet onPress={() => { void appendFoodLog('Skipped in app', 'Not done today', 'Skipped'); }} />
              <ActionButton label="Substituted" quiet onPress={() => { void appendFoodLog('Substitution note', 'Substitution used; edit details in source note', 'Needs review'); }} />
              <ActionButton label="Changed serving" quiet onPress={() => { void appendFoodLog('Serving change', 'Serving changed; update source nutrition if needed', 'Needs review'); }} />
              <ActionButton label="Add photo" quiet onPress={() => router.push({ pathname: '/capture', params: { type: 'Photo', targetRecordId: record.id } })} />
              <ActionButton label="Ask AI with this" quiet onPress={() => router.push('/chat')} />
            </View>
          </Card>
        ) : null}
        {foodDetail ? (
          <View style={styles.attackStrip}>
            <PlanCard label="Ready now" value={readySummary} tone="moss" />
            <PlanCard label={openIngredientCount ? 'Resolve before cooking' : 'No blocker'} value={missingSummary} tone={openIngredientCount ? 'amber' : 'moss'} />
            <PlanCard label="Next move" value={nextStep} tone="blue" />
          </View>
        ) : null}
      </Card>
    ) : null
  );

  const renderFoodIntelPanel = () => (
    foodDetail ? (
      <Card tone="moss" style={styles.intelPanel}>
        <View style={styles.intelHead}>
          <View>
            <Text style={[styles.intelKicker, { color: theme.colors.moss }]}>FOOD INTELLIGENCE</Text>
            <Text style={[styles.intelTitle, { color: theme.colors.ink }]}>Nutrition, availability, shopping and memory in one page.</Text>
          </View>
          <Pill tone={structuredFoodDetail ? 'moss' : 'amber'}>{structuredFoodDetail ? 'Source-backed' : 'Inferred until source sync'}</Pill>
        </View>
        <View style={styles.intelGrid}>
          <PlanCard label="Nutrition" value={primaryNutrition.map(([label, value]) => `${label}: ${value}`).join(' · ') || 'not captured'} tone="moss" />
          <PlanCard label="Available" value={readySummary} tone="moss" />
          <PlanCard label="Need / buy" value={missingSummary} tone={openIngredientCount ? 'amber' : 'moss'} />
          <PlanCard label="Memory" value={`${foodDetail.logs.length} logs · ${foodDetail.variations.length} variations · ${foodDetail.instructions.length} steps`} tone="plum" />
        </View>
      </Card>
    ) : null
  );

  const renderMainSection = (section: RecordSection) => {
    switch (section) {
      case 'nutrition':
        return foodDetail && recordConfig.showNutrition ? (
          <View key={section}>
            <SectionTitle title="Nutrition profile" />
            <View style={styles.sectionNoteRow}>
              <Pill tone={structuredFoodDetail ? 'moss' : 'amber'}>{structuredFoodDetail ? 'Source-backed' : 'Inferred'}</Pill>
              <Text style={[styles.sectionNote, { color: theme.colors.muted }]}>
                {nutritionSourceLabel}
              </Text>
            </View>
            <Card style={styles.servingPanel}>
              <View style={styles.servingHead}>
                <View>
                  <Text style={[styles.servingLabel, { color: theme.colors.muted }]}>Serving controls</Text>
                  <Text style={[styles.servingTitle, { color: theme.colors.ink }]}>{servingMultiplier}× · {servingLabel}</Text>
                </View>
                <View style={styles.servingButtons}>
                  {[1, 2, 3].map((count) => (
                    <Pressable
                      key={count}
                      accessibilityRole="button"
                      onPress={() => setServingMultiplier(count)}
                      style={({ pressed }) => [styles.servingButton, { backgroundColor: servingMultiplier === count ? theme.colors.ink : theme.colors.paper, borderColor: theme.colors.line }, pressed && { opacity: 0.72 }]}
                    >
                      <Text style={[styles.servingButtonText, { color: servingMultiplier === count ? theme.colors.paper : theme.colors.ink }]}>{count}×</Text>
                    </Pressable>
                  ))}
                </View>
              </View>
              <View style={styles.macroPanel}>
                <Text style={[styles.macroTitle, { color: theme.colors.ink }]}>Macro lens {caloriesRow ? `· ${caloriesRow.scaledValue}` : ''}</Text>
                {macroRows.length ? macroRows.map((row) => {
                  const amount = (row.parsed?.amount ?? 0) * servingMultiplier;
                  return (
                    <View key={row.label} style={styles.macroRow}>
                      <Text style={[styles.macroLabel, { color: theme.colors.muted }]}>{row.label}</Text>
                      <View style={[styles.macroTrack, { backgroundColor: theme.colors.canvas }]}>
                        <View style={[styles.macroFill, planToneStyle(macroColor(row.label), theme.colors), { width: `${Math.max(8, Math.min(100, (amount / macroMax) * 100))}%` }]} />
                      </View>
                      <Text style={[styles.macroValue, { color: theme.colors.ink }]}>{row.scaledValue}</Text>
                    </View>
                  );
                }) : (
                  <Text style={[styles.sectionNote, { color: theme.colors.muted }]}>No macro rows yet. Add protein, carbs, fat or fiber in the source profile.</Text>
                )}
              </View>
            </Card>
            <View style={styles.nutritionGrid}>
              {nutritionRows.map(({ label, scaledValue }) => (
                <Card key={label} style={styles.nutritionCard}>
                  <Text style={[styles.nutritionValue, { color: theme.colors.ink }]}>{scaledValue}</Text>
                  <Text style={[styles.nutritionLabel, { color: theme.colors.muted }]}>{label}</Text>
                </Card>
              ))}
            </View>
          </View>
        ) : null;
      case 'ingredients':
        return foodDetail && recordConfig.showIngredients ? (
          <View key={section}>
            <SectionTitle title="Ingredients and availability" />
            <View style={styles.ingredientBoard}>
              <IngredientGroup title="Available" items={ingredientsByState.available} tone="moss" />
              <IngredientGroup title="Needed" items={ingredientsByState.needed} tone="amber" />
              <IngredientGroup title="Shopping" items={ingredientsByState.shopping} tone="blue" />
              <IngredientGroup title="Previous / substitute" items={ingredientsByState.previous} tone="plum" />
            </View>
          </View>
        ) : null;
      case 'instructions':
        return foodDetail && recordConfig.showInstructions ? (
          <View key={section}>
            <SectionTitle title="Instructions" />
            <Card style={styles.listCard}>
              {foodDetail.instructions.length ? foodDetail.instructions.map((step, index) => (
                <View key={step} style={[styles.stepRow, { borderBottomColor: theme.colors.line }]}>
                  <Text style={[styles.stepNumber, { backgroundColor: theme.colors.mossSoft, color: theme.colors.moss }]}>{index + 1}</Text>
                  <Text style={[styles.stepText, { color: theme.colors.ink }]}>{step}</Text>
                </View>
              )) : (
                <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>No cooking instructions yet.</Text>
              )}
            </Card>
          </View>
        ) : null;
      case 'history':
        return foodDetail && recordConfig.showHistory ? (
          <View key={section}>
            <SectionTitle title="Cooking log and variations" />
            <View style={sharedStyles.grid}>
              <Card style={styles.historyCard}>
                <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>Previous notes</Text>
                {foodDetail.logs.map(([label, value]) => (
                  <View key={label} style={[styles.factRow, { borderTopColor: theme.colors.line }]}>
                    <Text style={[styles.factLabel, { color: theme.colors.muted }]}>{label}</Text>
                    <Text style={[styles.factValue, { color: theme.colors.ink }]}>{value}</Text>
                  </View>
                ))}
              </Card>
              <Card tone="plum" style={styles.historyCard}>
                <Text style={[styles.cardTitle, { color: theme.colors.ink }]}>Variations</Text>
                {foodDetail.variations.map((variation) => (
                  <Text key={variation} style={[styles.variation, { color: theme.colors.ink }]}>• {variation}</Text>
                ))}
              </Card>
            </View>
          </View>
        ) : null;
      case 'editableNote':
        return recordConfig.showEditableNote ? (
          <View key={section}>
            <SectionTitle title="Editable note" />
            <TextInput
              accessibilityLabel="Record details"
              value={body}
              onChangeText={setBody}
              style={[styles.editor, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line, color: theme.colors.ink }]}
              multiline
              textAlignVertical="top"
            />
            <View style={styles.actions}>
              <ActionButton label={dirty ? 'Save changes' : 'Saved'} onPress={handleSave} />
              {dirty ? <ActionButton label="Undo" quiet onPress={handleUndo} /> : null}
            </View>
          </View>
        ) : null;
      default:
        return null;
    }
  };

  const renderSideSection = (section: RecordSection) => {
    switch (section) {
      case 'properties':
        return recordConfig.showProperties ? (
          <View key={section}>
            <SectionTitle title="Properties" />
            <Card style={styles.sideCard}>
              <Fact label="Status" value={status} />
              <Fact label="Collection" value={pageLabel} />
              <Fact label="Source" value={sourceLabel} />
              <Fact label="Updated" value={updatedAt} />
              <Fact label="Detail" value={structuredFoodDetail ? 'Structured food_detail' : 'Inferred fallback'} />
            </Card>
            <SectionTitle title="Record properties" />
            <Card style={styles.sideCard}>
              <Text style={[styles.propertyHelp, { color: theme.colors.muted }]}>Edit the canonical properties for this collection. Notion, Sheets, SQLite and Chat preserve this object.</Text>
              <TextInput
                accessibilityLabel="Record properties JSON"
                value={propertiesDraft}
                onChangeText={setPropertiesDraft}
                multiline
                autoCapitalize="none"
                autoCorrect={false}
                textAlignVertical="top"
                style={[styles.propertyEditor, { backgroundColor: theme.colors.canvas, borderColor: theme.colors.line, color: theme.colors.ink }]}
              />
              <View style={styles.actions}>
                <ActionButton label={propertiesDirty ? 'Save properties' : 'Properties saved'} onPress={handleSaveProperties} />
                {propertiesDirty ? <ActionButton label="Undo" quiet onPress={() => setPropertiesDraft(savedPropertiesDraft)} /> : null}
              </View>
            </Card>
            {recordConfig.showNutrition && primaryNutrition.length ? (
              <>
                <SectionTitle title="At a glance" />
                <Card style={styles.sideCard}>
                  {primaryNutrition.map(([label, value]) => <Fact key={label} label={label} value={value} />)}
                </Card>
              </>
            ) : null}
          </View>
        ) : null;
      case 'relations':
        return recordConfig.showRelations ? (
          <View key={section}>
            <SectionTitle title="Connected records" />
            <View style={styles.relationGrid}>
              {relations.length ? (
                relations.map((relation) => {
                  const linked = linkedRecords[relation.target_id];
                  const relationTone = linked ? toTone(linked.properties.tone) : 'neutral';
                  return (
                    <Pressable
                      key={`${relation.name}:${relation.target_id}`}
                      accessibilityRole="link"
                      onPress={() => router.push(`/record/${relation.target_id}`)}
                        style={({ pressed }) => [styles.relationCard, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && { opacity: 0.7 }]}
                    >
                      <View style={styles.relationTop}>
                        <Text style={[styles.relationName, { color: theme.colors.moss }]}>{relation.name}</Text>
                        <Pill tone={relationTone}>{String(linked?.properties.status ?? 'Linked')}</Pill>
                      </View>
                      <Text style={[styles.relationTitle, { color: theme.colors.ink }]}>{linked?.title ?? relation.target_id}</Text>
                      <Text style={[styles.relationDetail, { color: theme.colors.muted }]}>
                        {linked
                          ? `${linked.collection} · ${String(linked.properties.meta ?? linked.source.provider)}`
                          : 'Linked item is not loaded on this device yet.'}
                      </Text>
                    </Pressable>
                  );
                })
              ) : (
                <Card style={styles.emptyRelationCard}>
                  <Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>No linked records yet.</Text>
                </Card>
              )}
            </View>
          </View>
        ) : null;
      case 'provenance':
        return recordConfig.showProvenance ? (
          <View key={section}>
            <SectionTitle title="Provenance" />
            <Card>
              <Row icon={visualGlyph(visualIdentity.sources?.[sourceLabel.toLowerCase().replace(/\s+/g, '_')], '🔗')} title={sourceLabel} detail="Canonical source" />
              <Row icon={visualGlyph(visualIdentity.domain, '◇')} title={`${catalog.activeManifest.label} structure v1`} detail="Properties and relations" />
            </Card>
          </View>
        ) : null;
      default:
        return null;
    }
  };

  return (
    <Page>
      <ScrollView keyboardShouldPersistTaps="handled">
        <View style={styles.pageContent}>
          <View style={styles.pageHead}>
            <Pressable accessibilityRole="button" onPress={() => router.back()} style={({ pressed }) => [styles.backButton, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && { opacity: 0.68 }]}>
              <Text style={[styles.backButtonText, { color: theme.colors.ink }]}>← Back</Text>
            </Pressable>
            <Text style={[styles.pageKicker, { color: theme.colors.moss }]}>LIFEOS / FOOD</Text>
            <Text style={[styles.pageTitle, { color: theme.colors.ink }]}>{pageLabel}</Text>
          </View>
          {recordSections.includes('hero') ? renderHeroSection() : null}
          {renderFoodIntelPanel()}

          <View style={[styles.workspace, compact && styles.workspaceCompact]}>
            <View style={styles.mainColumn}>
              {foodDetail ? (
                <>
              {!structuredFoodDetail ? (
                <Card tone="amber" style={styles.dataWarning}>
                  <Text style={[styles.warningTitle, { color: theme.colors.ink }]}>Rich Food detail missing from source</Text>
                  <Text style={[styles.warningBody, { color: theme.colors.muted }]}>
                    Showing inferred recipe detail from title/body. Add `food_detail` and `relations` in Notion, Sheets or SQLite to get full trusted nutrition, pantry state, shopping state, logs and variations.
                  </Text>
                </Card>
              ) : null}
                </>
              ) : null}
              {mainRecordSections.map(renderMainSection)}
            </View>

            <View style={[styles.sideColumn, compact && styles.sideColumnCompact]}>
              {sideRecordSections.map(renderSideSection)}
            </View>
          </View>
          <Pressable onPress={() => router.back()} style={styles.close}>
            <Text style={[styles.closeText, { color: theme.colors.muted }]}>Close record</Text>
          </Pressable>
          {notice ? <Text accessibilityLiveRegion="polite" style={[styles.notice, { color: theme.colors.moss }]}>{notice}</Text> : null}
        </View>
      </ScrollView>
    </Page>
  );
}

function IngredientGroup({ title, items, tone }: {
  title: string;
  items: FoodDetail['ingredients'];
  tone: 'moss' | 'amber' | 'blue' | 'plum';
}) {
  const theme = useLifeOSTheme();
  return (
    <Card tone={tone} style={styles.ingredientGroup}>
      <View style={styles.ingredientGroupHead}>
        <Text style={[styles.ingredientGroupTitle, { color: theme.colors.ink }]}>{title}</Text>
        <Pill tone={tone}>{items.length}</Pill>
      </View>
      {items.length ? items.map((ingredient) => (
        <View key={`${title}-${ingredient.name}-${ingredient.amount}`} style={[styles.ingredientMini, { borderTopColor: theme.colors.line }]}>
          <Text style={[styles.ingredientName, { color: theme.colors.ink }]}>{ingredient.name}</Text>
          <Text style={[styles.ingredientAmount, { color: theme.colors.muted }]}>{ingredient.amount}</Text>
        </View>
      )) : (
        <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>None captured.</Text>
      )}
    </Card>
  );
}

function PlanCard({ label, value, tone }: { label: string; value: string; tone: 'moss' | 'amber' | 'blue' | 'plum' }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.planCard, planToneStyle(tone, theme.colors)]}>
      <Text style={[styles.planLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text style={[styles.planValue, { color: theme.colors.ink }]} numberOfLines={3}>{value}</Text>
    </View>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.sideFact, { borderTopColor: theme.colors.line }]}>
      <Text style={[styles.sideFactLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text style={[styles.sideFactValue, { color: theme.colors.ink }]}>{value}</Text>
    </View>
  );
}

function planToneStyle(tone: 'moss' | 'amber' | 'blue' | 'plum', themed: typeof colors) {
  if (tone === 'amber') return { backgroundColor: themed.amberSoft, borderColor: themed.line };
  if (tone === 'blue') return { backgroundColor: themed.blueSoft, borderColor: themed.line };
  if (tone === 'plum') return { backgroundColor: themed.plumSoft, borderColor: themed.line };
  return { backgroundColor: themed.mossSoft, borderColor: themed.line };
}

const styles = StyleSheet.create({
  pageContent: { width: '100%', maxWidth: 1380, alignSelf: 'center', paddingHorizontal: 20, paddingBottom: 120 },
  pageHead: { paddingTop: 20 },
  backButton: { alignSelf: 'flex-start', minHeight: 40, borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, backgroundColor: colors.paper, paddingHorizontal: 14, justifyContent: 'center', marginBottom: 18 },
  backButtonText: { color: colors.ink, fontSize: 13, fontWeight: '900' },
  pageKicker: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.6 },
  pageTitle: { color: colors.ink, fontSize: 28, lineHeight: 34, fontWeight: '900', letterSpacing: -0.8, marginTop: 6 },
  hero: { marginTop: 18, padding: 24, minHeight: 210 },
  intelPanel: { marginTop: 14, padding: 18 },
  intelHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 },
  intelKicker: { color: colors.moss, fontSize: 10, fontWeight: '900', letterSpacing: 1.1, textTransform: 'uppercase' },
  intelTitle: { color: colors.ink, fontSize: 18, lineHeight: 23, fontWeight: '900', marginTop: 5, maxWidth: 720 },
  intelGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 14 },
  workspace: { flexDirection: 'row', alignItems: 'flex-start', gap: 18 },
  workspaceCompact: { flexDirection: 'column' },
  mainColumn: { flex: 1, minWidth: 0 },
  sideColumn: { width: 340 },
  sideColumnCompact: { width: '100%' },
  sideCard: { gap: 0 },
  statusRow: { paddingTop: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  sync: { color: colors.muted, fontSize: 11 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, marginTop: 18, padding: 0 },
  meta: { color: colors.muted, fontSize: 13, marginTop: 8 },
  heroBody: { color: colors.ink, fontSize: 16, lineHeight: 24, marginTop: 14, maxWidth: 820 },
  heroEvidence: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 18 },
  heroEvidenceCell: { flexGrow: 1, flexBasis: 128, minHeight: 72, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 12, justifyContent: 'center' },
  heroEvidenceValue: { color: colors.ink, fontSize: 15, lineHeight: 19, fontWeight: '900' },
  heroEvidenceLabel: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 0.8, textTransform: 'uppercase', marginTop: 6 },
  editor: { minHeight: 150, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 16, color: colors.ink, fontSize: 15, lineHeight: 23 },
  actions: { flexDirection: 'row', gap: 9, marginTop: 12 },
  quickActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, marginTop: 18 },
  actionPanel: { marginTop: 14, padding: 14 },
  actionPanelHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 10 },
  actionTitle: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  actionClose: { color: colors.muted, fontSize: 12, fontWeight: '800' },
  actionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  attackStrip: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 18 },
  planCard: { flexGrow: 1, flexBasis: 220, minHeight: 86, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: 13, justifyContent: 'center' },
  planLabel: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 0.9, textTransform: 'uppercase' },
  planValue: { color: colors.ink, fontSize: 14, lineHeight: 19, fontWeight: '900', marginTop: 7 },
  dataWarning: { marginTop: 18 },
  warningTitle: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  warningBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 6 },
  sectionNoteRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 8, marginBottom: 10 },
  sectionNote: { color: colors.muted, fontSize: 12, lineHeight: 17, flex: 1, minWidth: 180 },
  servingPanel: { marginBottom: 12, padding: 14 },
  servingHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 },
  servingLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 1, textTransform: 'uppercase' },
  servingTitle: { color: colors.ink, fontSize: 18, fontWeight: '900', marginTop: 4 },
  servingButtons: { flexDirection: 'row', gap: 8 },
  servingButton: { minWidth: 46, minHeight: 38, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  servingButtonText: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  macroPanel: { marginTop: 14, gap: 8 },
  macroTitle: { color: colors.ink, fontSize: 14, fontWeight: '900', marginBottom: 2 },
  macroRow: { minHeight: 28, flexDirection: 'row', alignItems: 'center', gap: 10 },
  macroLabel: { color: colors.muted, width: 72, fontSize: 11, fontWeight: '900' },
  macroTrack: { flex: 1, height: 10, borderRadius: 8, overflow: 'hidden', backgroundColor: colors.canvas },
  macroFill: { height: 10, borderRadius: 8, borderWidth: 0 },
  macroValue: { color: colors.ink, width: 70, textAlign: 'right', fontSize: 12, fontWeight: '900' },
  nutritionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  nutritionCard: { flexGrow: 1, flexBasis: 130, minHeight: 92 },
  nutritionValue: { color: colors.ink, fontSize: 20, fontWeight: '900', letterSpacing: -0.5 },
  nutritionLabel: { color: colors.muted, fontSize: 11, fontWeight: '800', marginTop: 6, textTransform: 'uppercase' },
  listCard: { paddingVertical: 0 },
  ingredientBoard: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  ingredientGroup: { flexGrow: 1, flexBasis: 240, minHeight: 150 },
  ingredientGroupHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginBottom: 10 },
  ingredientGroupTitle: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  ingredientMini: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, paddingVertical: 9 },
  ingredientRow: { minHeight: 66, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  ingredientCopy: { flex: 1, minWidth: 0 },
  ingredientName: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  ingredientAmount: { color: colors.muted, fontSize: 12, marginTop: 4 },
  stepRow: { minHeight: 58, flexDirection: 'row', gap: 12, alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  stepNumber: { width: 30, height: 30, borderRadius: 15, overflow: 'hidden', textAlign: 'center', lineHeight: 30, backgroundColor: colors.mossSoft, color: colors.moss, fontWeight: '900' },
  stepText: { color: colors.ink, fontSize: 14, lineHeight: 20, flex: 1 },
  historyCard: { flexGrow: 1, flexBasis: 280, minHeight: 150 },
  cardTitle: { color: colors.ink, fontSize: 16, fontWeight: '900', marginBottom: 12 },
  factRow: { flexDirection: 'row', justifyContent: 'space-between', gap: 12, paddingVertical: 7, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  factLabel: { color: colors.muted, fontSize: 12, fontWeight: '800' },
  factValue: { color: colors.ink, fontSize: 12, fontWeight: '800', flex: 1, textAlign: 'right' },
  variation: { color: colors.ink, fontSize: 13, lineHeight: 20, marginTop: 4 },
  sideFact: { paddingVertical: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  sideFactLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 0.8, textTransform: 'uppercase' },
  sideFactValue: { color: colors.ink, fontSize: 13, lineHeight: 18, fontWeight: '800', marginTop: 4 },
  propertyHelp: { color: colors.muted, fontSize: 12, lineHeight: 18, marginBottom: 10 },
  propertyEditor: { minHeight: 180, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.canvas, padding: 12, color: colors.ink, fontFamily: 'monospace', fontSize: 12, lineHeight: 18 },
  relationGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  relationCard: { flexGrow: 1, flexBasis: 240, minHeight: 128, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 14 },
  relationTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 10, alignItems: 'center' },
  relationName: { color: colors.moss, fontSize: 10, fontWeight: '900', letterSpacing: 1.1, textTransform: 'uppercase' },
  relationTitle: { color: colors.ink, fontSize: 17, fontWeight: '900', marginTop: 18 },
  relationDetail: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 5 },
  emptyRelationCard: { flex: 1 },
  close: { alignSelf: 'center', padding: 18, marginTop: 20 },
  closeText: { color: colors.muted, fontSize: 13, fontWeight: '700' },
  notice: { color: colors.moss, fontSize: 13, fontWeight: '800', textAlign: 'center', marginBottom: 24 },
  emptyTitle: { color: colors.ink, marginTop: 22, fontSize: 16, fontWeight: '800' },
  emptyBody: { color: colors.muted, marginTop: 6 },
  loading: { color: colors.muted, marginTop: 22 },
});
