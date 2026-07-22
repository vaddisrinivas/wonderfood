import { useState } from 'react';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { ActionButton, Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { colors, radius } from '@/src/theme';

const types = [['Note', '✎'], ['Food', '◉'], ['Receipt', '▤'], ['Voice', '◖'], ['Link', '⌁']];

export default function CaptureScreen() {
  const router = useRouter(); const [type, setType] = useState('Note'); const [value, setValue] = useState(''); const [saved, setSaved] = useState(false);
  return <Page><ScrollView keyboardShouldPersistTaps="handled"><View style={sharedStyles.content}>
    <PageHeader eyebrow="Inbox first, organize later" title="Capture anything." subtitle="LifeOS will classify it, connect related records and preserve the original source." />
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.types}>{types.map(([name, icon]) => <Pressable key={name} accessibilityRole="button" accessibilityState={{ selected: type === name }} onPress={() => setType(name)} style={[styles.type, type === name && styles.typeActive]}><Text>{icon}</Text><Text style={[styles.typeText, type === name && styles.typeTextActive]}>{name}</Text></Pressable>)}</ScrollView>
    <Card style={styles.editorCard}><View style={styles.editorTop}><Pill tone="moss">{type.toUpperCase()}</Pill><Text style={styles.destination}>→ Food inbox</Text></View><TextInput value={value} onChangeText={(text) => { setValue(text); setSaved(false); }} multiline autoFocus placeholder={type === 'Food' ? 'Add 2 avocados and yogurt to shopping…' : 'Type, paste, dictate or scan…'} placeholderTextColor={colors.muted} style={styles.input} textAlignVertical="top" /><View style={styles.attachments}><Text style={styles.attachment}>▧ Photo</Text><Text style={styles.attachment}>⌁ Link</Text><Text style={styles.attachment}>◖ Record</Text></View></Card>
    <Text style={styles.hint}>AI suggests a destination, properties and relations. The original capture is always retained.</Text>
    {saved ? <Card tone="moss" style={styles.success}><Text style={styles.successTitle}>Captured to Food inbox</Text><Text style={sharedStyles.muted}>Classification will run when connected. You can undo from Today.</Text></Card> : null}
    <View style={styles.actions}><ActionButton label="Save capture" disabled={!value.trim()} onPress={() => setSaved(true)} /><ActionButton label="Cancel" quiet onPress={() => router.back()} /></View>
  </View></ScrollView></Page>;
}

const styles = StyleSheet.create({
  types: { gap: 9, marginBottom: 15 }, type: { minWidth: 76, paddingHorizontal: 14, paddingVertical: 11, borderRadius: radius.pill, backgroundColor: '#E9E8DF', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 }, typeActive: { backgroundColor: colors.ink }, typeText: { color: colors.ink, fontSize: 12, fontWeight: '800' }, typeTextActive: { color: '#FFF' }, editorCard: { minHeight: 260 }, editorTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, destination: { color: colors.muted, fontSize: 11, fontWeight: '700' }, input: { flex: 1, minHeight: 150, color: colors.ink, fontSize: 18, lineHeight: 27, paddingVertical: 18 }, attachments: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, paddingTop: 13, flexDirection: 'row', gap: 18 }, attachment: { color: colors.muted, fontSize: 12, fontWeight: '700' }, hint: { color: colors.muted, fontSize: 11, lineHeight: 17, margin: 12 }, success: { marginTop: 6 }, successTitle: { color: colors.ink, fontSize: 14, fontWeight: '800', marginBottom: 4 }, actions: { flexDirection: 'row', gap: 9, marginTop: 18 },
});
