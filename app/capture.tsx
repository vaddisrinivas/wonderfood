import { useState } from 'react';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { ActionButton, Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { colors, radius } from '@/src/theme';
import { useLifeOSDatabase } from '@/src/db/provider';
import { loadCatalog } from '@/src/domain/catalog';
import { upsertRecord } from '@/src/db/records';

const types = [['Note', '✎'], ['Food', '◉'], ['Receipt', '▤'], ['Voice', '◖'], ['Link', '⌁']] as const;

type CaptureType = (typeof types)[number][0];

function mapCaptureCollection(captureType: CaptureType): string {
  if (captureType === 'Food') return 'shopping_item';
  if (captureType === 'Receipt' || captureType === 'Voice') return 'purchase';
  return 'source_record';
}

export default function CaptureScreen() {
  const router = useRouter();
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();

  const [type, setType] = useState<CaptureType>('Note');
  const [value, setValue] = useState('');
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const title = value.trim().split('\n')[0]?.slice(0, 70) || `${catalog.activeManifest.label} note`;
  const hasLocalGraph = Boolean(db);

  const handleSave = async () => {
    if (!value.trim()) {
      return;
    }

    if (!db) {
      setSaved(true);
      return;
    }

    setSaving(true);
    const now = new Date().toISOString();
    const recordId = `capture-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

    try {
      await upsertRecord(
        db,
        catalog.activeManifest,
        {
          id: recordId,
          title,
          collection: mapCaptureCollection(type),
          properties: {
            status: 'Active',
            tone: 'moss',
            meta: `${type} capture from local inbox`,
            body: value.trim(),
            source: `${type} · ${hasLocalGraph ? 'SQLite' : 'fallback'}`,
          },
          source: {
            provider: 'user',
            external_id: recordId,
            url: null,
            observed_at: now,
            content_hash: null,
          },
          archived_at: null,
          created_at: now,
          updated_at: now,
        }
      );
      setSaved(true);
      setValue('');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Page>
      <ScrollView keyboardShouldPersistTaps="handled">
        <View style={sharedStyles.content}>
          <PageHeader
            eyebrow="Inbox first, organize later"
            title="Capture anything."
            subtitle="LifeOS will classify it, connect related records and preserve the original source."
          />
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.types}>
            {types.map(([name, icon]) => (
              <Pressable
                key={name}
                accessibilityRole="button"
                accessibilityState={{ selected: type === name }}
                onPress={() => {
                  setType(name);
                  setSaved(false);
                }}
                style={[styles.type, type === name && styles.typeActive]}
              >
                <Text>{icon}</Text>
                <Text style={[styles.typeText, type === name && styles.typeTextActive]}>{name}</Text>
              </Pressable>
            ))}
          </ScrollView>
          <Card style={styles.editorCard}>
            <View style={styles.editorTop}>
              <Pill tone="moss">{type.toUpperCase()}</Pill>
              <Text style={styles.destination}>→ {hasLocalGraph ? `${catalog.activeManifest.label} graph` : 'Food inbox (preview)'}</Text>
            </View>
            <TextInput
              value={value}
              onChangeText={(text) => {
                setValue(text);
                setSaved(false);
              }}
              multiline
              autoFocus
              placeholder={type === 'Food' ? 'Add 2 avocados and yogurt to shopping…' : 'Type, paste, dictate or scan…'}
              placeholderTextColor={colors.muted}
              style={styles.input}
              textAlignVertical="top"
            />
            <View style={styles.attachments}>
              <Text style={styles.attachment}>▧ Photo</Text>
              <Text style={styles.attachment}>⌁ Link</Text>
              <Text style={styles.attachment}>◖ Record</Text>
            </View>
          </Card>
          <Text style={styles.hint}>
            {hasLocalGraph
              ? `Writes to ${catalog.activeManifest.label} local graph with no network dependency.`
              : 'No local graph yet. Capture is kept in-session for this phase.'}
          </Text>
          {saved ? (
            <Card tone="moss" style={styles.success}>
              <Text style={styles.successTitle}>Captured</Text>
              <Text style={sharedStyles.muted}>Stored in {hasLocalGraph ? `${catalog.activeManifest.label} graph` : 'session fallback'}.</Text>
            </Card>
          ) : null}
          <View style={styles.actions}>
            <ActionButton label="Save capture" disabled={!value.trim() || saving} onPress={() => void handleSave()} />
            <ActionButton label="Cancel" quiet onPress={() => router.back()} />
          </View>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  types: { gap: 9, marginBottom: 15 },
  type: { minWidth: 76, paddingHorizontal: 14, paddingVertical: 11, borderRadius: radius.pill, backgroundColor: '#E9E8DF', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  typeActive: { backgroundColor: colors.ink },
  typeText: { color: colors.ink, fontSize: 12, fontWeight: '800' },
  typeTextActive: { color: '#FFF' },
  editorCard: { minHeight: 260 },
  editorTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  destination: { color: colors.muted, fontSize: 11, fontWeight: '700' },
  input: { flex: 1, minHeight: 150, color: colors.ink, fontSize: 18, lineHeight: 27, paddingVertical: 18 },
  attachments: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, paddingTop: 13, flexDirection: 'row', gap: 18 },
  attachment: { color: colors.muted, fontSize: 12, fontWeight: '700' },
  hint: { color: colors.muted, fontSize: 11, lineHeight: 17, margin: 12 },
  success: { marginTop: 6 },
  successTitle: { color: colors.ink, fontSize: 14, fontWeight: '800', marginBottom: 4 },
  actions: { flexDirection: 'row', gap: 9, marginTop: 18 },
});
