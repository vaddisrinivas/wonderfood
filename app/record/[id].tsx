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

export default function RecordScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { width } = useWindowDimensions();
  const compact = width < 860;
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();
  const settings = useLifeOSSettingsSnapshot();
  const theme = useLifeOSTheme();

  const [record, setRecord] = useState<CanonicalRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [saved, setSaved] = useState({ title: '', body: '' });
  const [linkedRecords, setLinkedRecords] = useState<Record<string, CanonicalRecord>>({});
  const [notice, setNotice] = useState('');

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
      setLoading(false);
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [db, id, catalog]);

  const dirty = title !== saved.title || body !== saved.body;
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

  const persistRecordPatch = async (patch: Partial<CanonicalRecord>) => {
    if (!record || !db) {
      setNotice('Saved locally in this view. Open Settings to connect a writable graph.');
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
    const stamped = new Date().toLocaleString();
    const nextDetail = {
      ...foodDetail,
      logs: [
        ['Logged in app', stamped],
        ...foodDetail.logs.filter(([label]) => label !== 'Logged in app'),
      ],
    };
    await persistRecordPatch({
      properties: {
        food_detail: nextDetail,
        status: foodDetail.kind === 'shopping' ? 'Review' : 'Logged',
      },
    });
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
            <Text style={[styles.emptyBody, { color: theme.colors.muted }]}>This record is not in the current domain graph.</Text>
            <Pressable onPress={() => router.back()} style={styles.close}>
              <Text style={[styles.closeText, { color: theme.colors.muted }]}>Close</Text>
            </Pressable>
          </View>
        </ScrollView>
      </Page>
    );
  }

  const recordSections = orderedRecordSections(recordConfig.sectionOrder);
  const mainRecordSections = recordSections.filter((section) => MAIN_RECORD_SECTIONS.has(section));
  const sideRecordSections = recordSections.filter((section) => SIDE_RECORD_SECTIONS.has(section));

  const renderHeroSection = () => (
    recordConfig.showHero ? (
      <Card key="hero" tone={tone === 'neutral' ? undefined : tone} style={styles.hero}>
        <View style={styles.statusRow}>
          <Pill tone={tone}>{status}</Pill>
          <Text style={[styles.sync, { color: theme.colors.muted }]}>Updated {updatedAt}</Text>
        </View>
        <TextInput accessibilityLabel="Record title" value={title} onChangeText={setTitle} style={[styles.title, { color: theme.colors.ink }]} multiline />
        <Text style={[styles.meta, { color: theme.colors.muted }]}>{record.collection} · {meta}</Text>
        <Text style={[styles.heroBody, { color: theme.colors.ink }]}>{body || 'No note yet.'}</Text>
        <View style={styles.quickActions}>
          <ActionButton label="Ask about this" quiet onPress={() => router.push('/chat')} />
          <ActionButton label={foodDetail?.kind === 'recipe' || foodDetail?.kind === 'meal' ? 'Cook / log' : 'Use / update'} quiet onPress={() => { void handlePrimaryFoodAction(); }} />
          <ActionButton label="Add missing" quiet onPress={() => router.push('/(tabs)/food')} />
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
            <View style={styles.nutritionGrid}>
              {foodDetail.nutrition.slice(0, nutritionLimit).map(([label, value]) => (
                <Card key={label} style={styles.nutritionCard}>
                  <Text style={[styles.nutritionValue, { color: theme.colors.ink }]}>{value}</Text>
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
              <Fact label="Collection" value={record.collection} />
              <Fact label="Source" value={sourceLabel} />
              <Fact label="Updated" value={updatedAt} />
              <Fact label="Detail" value={structuredFoodDetail ? 'Structured food_detail' : 'Inferred fallback'} />
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
                          : 'Linked record not loaded in this local graph yet.'}
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
              <Row icon="S" title={sourceLabel} detail="Canonical source" />
              <Row icon="⌁" title="LifeOS Food schema v1" detail="Record shape and relations" />
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
          {recordSections.includes('hero') ? renderHeroSection() : null}

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

function Fact({ label, value }: { label: string; value: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.sideFact, { borderTopColor: theme.colors.line }]}>
      <Text style={[styles.sideFactLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text style={[styles.sideFactValue, { color: theme.colors.ink }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pageContent: { width: '100%', maxWidth: 1380, alignSelf: 'center', paddingHorizontal: 20, paddingBottom: 120 },
  hero: { marginTop: 18, padding: 26, minHeight: 230 },
  workspace: { flexDirection: 'row', alignItems: 'flex-start', gap: 18 },
  workspaceCompact: { flexDirection: 'column' },
  mainColumn: { flex: 1, minWidth: 0 },
  sideColumn: { width: 340 },
  sideColumnCompact: { width: '100%' },
  sideCard: { gap: 0 },
  statusRow: { paddingTop: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  sync: { color: colors.muted, fontSize: 11 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, marginTop: 22, padding: 0 },
  meta: { color: colors.muted, fontSize: 13, marginTop: 8 },
  heroBody: { color: colors.ink, fontSize: 16, lineHeight: 24, marginTop: 14, maxWidth: 820 },
  editor: { minHeight: 150, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 16, color: colors.ink, fontSize: 15, lineHeight: 23 },
  actions: { flexDirection: 'row', gap: 9, marginTop: 12 },
  quickActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, marginTop: 18 },
  dataWarning: { marginTop: 18 },
  warningTitle: { color: colors.ink, fontSize: 15, fontWeight: '900' },
  warningBody: { color: colors.muted, fontSize: 13, lineHeight: 19, marginTop: 6 },
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
