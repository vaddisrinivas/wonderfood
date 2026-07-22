import { useLocalSearchParams, useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { ActionButton, Card, Page, Pill, Row, SectionTitle, sharedStyles } from '@/src/components/ui';
import { foodRecords } from '@/src/data/sample';
import { colors, radius } from '@/src/theme';

export default function RecordScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const record = useMemo(() => foodRecords.find((item) => item.id === id) ?? foodRecords[0], [id]);
  const [title, setTitle] = useState(record.title);
  const [body, setBody] = useState(record.body);
  const [saved, setSaved] = useState({ title: record.title, body: record.body });
  const dirty = title !== saved.title || body !== saved.body;

  return (
    <Page><ScrollView keyboardShouldPersistTaps="handled"><View style={sharedStyles.content}>
      <View style={styles.statusRow}><Pill tone={record.tone}>{record.status}</Pill><Text style={styles.sync}>Saved locally · sync ready</Text></View>
      <TextInput accessibilityLabel="Record title" value={title} onChangeText={setTitle} style={styles.title} multiline />
      <Text style={styles.meta}>{record.meta}</Text>
      <SectionTitle title="Details" />
      <TextInput accessibilityLabel="Record details" value={body} onChangeText={setBody} style={styles.editor} multiline textAlignVertical="top" />
      <View style={styles.actions}>
        <ActionButton label={dirty ? 'Save changes' : 'Saved'} onPress={() => setSaved({ title, body })} />
        {dirty ? <ActionButton label="Undo" quiet onPress={() => { setTitle(saved.title); setBody(saved.body); }} /> : null}
      </View>
      <SectionTitle title="Connected records" />
      <Card style={styles.listCard}>
        <Row icon="◒" title="Tonight’s meal plan" detail="Wednesday · Dinner" href="/record/recipe-tandoori" />
        <Row icon="▦" title="Whole Foods receipt" detail="Purchase · July 20" />
        <Row icon="✦" title="Meal planning conversation" detail="AI thread · 3 citations" href="/(tabs)/chat" />
      </Card>
      <SectionTitle title="Provenance" />
      <Card><Row icon="N" title={record.source} detail="Canonical source · synced 2 minutes ago" /><Row icon="⌁" title="LifeOS Food schema v1" detail="Record shape and relations" /></Card>
      <Pressable onPress={() => router.back()} style={styles.close}><Text style={styles.closeText}>Close record</Text></Pressable>
    </View></ScrollView></Page>
  );
}

const styles = StyleSheet.create({
  statusRow: { paddingTop: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  sync: { color: colors.muted, fontSize: 11 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, marginTop: 22, padding: 0 },
  meta: { color: colors.muted, fontSize: 13, marginTop: 8 },
  editor: { minHeight: 150, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, backgroundColor: colors.paper, padding: 16, color: colors.ink, fontSize: 15, lineHeight: 23 },
  actions: { flexDirection: 'row', gap: 9, marginTop: 12 }, listCard: { paddingVertical: 0 }, close: { alignSelf: 'center', padding: 18, marginTop: 20 }, closeText: { color: colors.muted, fontSize: 13, fontWeight: '700' },
});
