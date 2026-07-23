import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { ActionButton, Card, Page, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { colors, radius } from '@/src/theme';
import { useLifeOSDatabase } from '@/src/db/provider';
import { getDomainRecord, getDomainRecordCanonical } from '@/src/domain/queries';
import { loadCatalog } from '@/src/domain/catalog';
import { CanonicalRecord } from '@/src/domain/runtime';
import { upsertRecord } from '@/src/db/records';

type FoodDetail = {
  kind: 'meal' | 'recipe' | 'inventory' | 'shopping' | 'generic';
  nutrition: Array<[string, string]>;
  ingredients: Array<{ name: string; amount: string; state: 'available' | 'needed' | 'shopping' | 'previous' }>;
  instructions: string[];
  logs: Array<[string, string]>;
  variations: string[];
};

const ingredientStates = new Set(['available', 'needed', 'shopping', 'previous']);

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
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();

  const [record, setRecord] = useState<CanonicalRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [saved, setSaved] = useState({ title: '', body: '' });

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

  if (loading) {
    return (
      <Page>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            <Text style={styles.loading}>Loading record…</Text>
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
            <Text style={styles.emptyTitle}>Record not found</Text>
            <Text style={styles.emptyBody}>This record is not in the current domain graph.</Text>
            <Pressable onPress={() => router.back()} style={styles.close}>
              <Text style={styles.closeText}>Close</Text>
            </Pressable>
          </View>
        </ScrollView>
      </Page>
    );
  }

  return (
    <Page>
      <ScrollView keyboardShouldPersistTaps="handled">
        <View style={sharedStyles.content}>
          <View style={styles.statusRow}>
            <Pill tone={tone}>{status}</Pill>
            <Text style={styles.sync}>Updated {updatedAt}</Text>
          </View>
          <TextInput accessibilityLabel="Record title" value={title} onChangeText={setTitle} style={styles.title} multiline />
          <Text style={styles.meta}>{meta}</Text>
          {foodDetail ? (
            <>
              <View style={styles.quickActions}>
                <ActionButton label="Ask about this" quiet onPress={() => router.push('/chat')} />
                <ActionButton label={foodDetail.kind === 'recipe' || foodDetail.kind === 'meal' ? 'Cook / log' : 'Use / update'} quiet onPress={() => {}} />
                <ActionButton label="Add missing" quiet onPress={() => router.push('/(tabs)/food')} />
              </View>

              <SectionTitle title="Nutrition profile" />
              <View style={styles.nutritionGrid}>
                {foodDetail.nutrition.map(([label, value]) => (
                  <Card key={label} style={styles.nutritionCard}>
                    <Text style={styles.nutritionValue}>{value}</Text>
                    <Text style={styles.nutritionLabel}>{label}</Text>
                  </Card>
                ))}
              </View>

              <SectionTitle title="Ingredients and availability" />
              <Card style={styles.listCard}>
                {foodDetail.ingredients.length ? foodDetail.ingredients.map((ingredient) => (
                  <View key={`${ingredient.name}-${ingredient.state}`} style={styles.ingredientRow}>
                    <View style={styles.ingredientCopy}>
                      <Text style={styles.ingredientName}>{ingredient.name}</Text>
                      <Text style={styles.ingredientAmount}>{ingredient.amount}</Text>
                    </View>
                    <Pill tone={stateTone(ingredient.state)}>{ingredient.state.toUpperCase()}</Pill>
                  </View>
                )) : (
                  <Text style={sharedStyles.muted}>No structured ingredients yet.</Text>
                )}
              </Card>

              <SectionTitle title="Instructions" />
              <Card style={styles.listCard}>
                {foodDetail.instructions.length ? foodDetail.instructions.map((step, index) => (
                  <View key={step} style={styles.stepRow}>
                    <Text style={styles.stepNumber}>{index + 1}</Text>
                    <Text style={styles.stepText}>{step}</Text>
                  </View>
                )) : (
                  <Text style={sharedStyles.muted}>No cooking instructions yet.</Text>
                )}
              </Card>

              <SectionTitle title="Cooking log and variations" />
              <View style={sharedStyles.grid}>
                <Card style={styles.historyCard}>
                  <Text style={styles.cardTitle}>Previous notes</Text>
                  {foodDetail.logs.map(([label, value]) => (
                    <View key={label} style={styles.factRow}>
                      <Text style={styles.factLabel}>{label}</Text>
                      <Text style={styles.factValue}>{value}</Text>
                    </View>
                  ))}
                </Card>
                <Card tone="plum" style={styles.historyCard}>
                  <Text style={styles.cardTitle}>Variations</Text>
                  {foodDetail.variations.map((variation) => (
                    <Text key={variation} style={styles.variation}>• {variation}</Text>
                  ))}
                </Card>
              </View>
            </>
          ) : null}

          <SectionTitle title="Editable note" />
          <TextInput
            accessibilityLabel="Record details"
            value={body}
            onChangeText={setBody}
            style={styles.editor}
            multiline
            textAlignVertical="top"
          />
          <View style={styles.actions}>
            <ActionButton label={dirty ? 'Save changes' : 'Saved'} onPress={handleSave} />
            {dirty ? <ActionButton label="Undo" quiet onPress={handleUndo} /> : null}
          </View>
          <SectionTitle title="Connected records" />
          <Card style={styles.listCard}>
            {relations.length ? (
              relations.map((relation) => (
                <Row
                  key={`${relation.name}:${relation.target_id}`}
                  icon="◒"
                  title={relation.name}
                  detail={relation.target_id}
                  href={{ pathname: '/record/[id]', params: { id: relation.target_id } }}
                />
              ))
            ) : (
              <Text style={sharedStyles.muted}>No linked records yet.</Text>
            )}
          </Card>
          <SectionTitle title="Provenance" />
          <Card>
            <Row icon="S" title={sourceLabel} detail="Canonical source" />
            <Row icon="⌁" title="LifeOS Food schema v1" detail="Record shape and relations" />
          </Card>
          <Pressable onPress={() => router.back()} style={styles.close}>
            <Text style={styles.closeText}>Close record</Text>
          </Pressable>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  statusRow: { paddingTop: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  sync: { color: colors.muted, fontSize: 11 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, marginTop: 22, padding: 0 },
  meta: { color: colors.muted, fontSize: 13, marginTop: 8 },
  editor: { minHeight: 150, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 16, color: colors.ink, fontSize: 15, lineHeight: 23 },
  actions: { flexDirection: 'row', gap: 9, marginTop: 12 },
  quickActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, marginTop: 18 },
  nutritionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  nutritionCard: { flexGrow: 1, flexBasis: 130, minHeight: 92 },
  nutritionValue: { color: colors.ink, fontSize: 20, fontWeight: '900', letterSpacing: -0.5 },
  nutritionLabel: { color: colors.muted, fontSize: 11, fontWeight: '800', marginTop: 6, textTransform: 'uppercase' },
  listCard: { paddingVertical: 0 },
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
  close: { alignSelf: 'center', padding: 18, marginTop: 20 },
  closeText: { color: colors.muted, fontSize: 13, fontWeight: '700' },
  emptyTitle: { color: colors.ink, marginTop: 22, fontSize: 16, fontWeight: '800' },
  emptyBody: { color: colors.muted, marginTop: 6 },
  loading: { color: colors.muted, marginTop: 22 },
});
