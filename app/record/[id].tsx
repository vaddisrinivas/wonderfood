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

function fallbackCanonicalFromView(
  view: Awaited<ReturnType<typeof getDomainRecord>>,
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
          <SectionTitle title="Details" />
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
  listCard: { paddingVertical: 0 },
  close: { alignSelf: 'center', padding: 18, marginTop: 20 },
  closeText: { color: colors.muted, fontSize: 13, fontWeight: '700' },
  emptyTitle: { color: colors.ink, marginTop: 22, fontSize: 16, fontWeight: '800' },
  emptyBody: { color: colors.muted, marginTop: 6 },
  loading: { color: colors.muted, marginTop: 22 },
});
