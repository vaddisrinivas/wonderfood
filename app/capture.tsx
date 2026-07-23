import { useEffect, useState } from 'react';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Image, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { ActionButton, Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { colors, radius, useLifeOSTheme } from '@/src/theme';
import { useLifeOSDatabase } from '@/src/db/provider';
import { loadCatalog } from '@/src/domain/catalog';
import { upsertRecord } from '@/src/db/records';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

const types = [['Note', '✎'], ['Food', '◉'], ['Receipt', '▤'], ['Photo', '▧'], ['Voice', '◖'], ['Link', '⌁']] as const;

type CaptureType = (typeof types)[number][0];
const CAPTURE_SECTIONS = ['hero', 'typePicker', 'editor', 'routeCard'] as const;
type CaptureSection = typeof CAPTURE_SECTIONS[number];

function mapCaptureCollection(captureType: CaptureType): string {
  if (captureType === 'Food') return 'shopping_item';
  if (captureType === 'Receipt' || captureType === 'Voice') return 'purchase';
  if (captureType === 'Photo') return 'source_record';
  return 'source_record';
}

function orderedSections(value: string) {
  const allowed = new Set<string>(CAPTURE_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is CaptureSection => allowed.has(section));
  const missing = CAPTURE_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

export default function CaptureScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ type?: string; targetRecordId?: string }>();
  const db = useLifeOSDatabase();
  const catalog = loadCatalog();
  const theme = useLifeOSTheme();
  const settings = useLifeOSSettingsSnapshot();

  const [type, setType] = useState<CaptureType>('Note');
  const [value, setValue] = useState('');
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [photos, setPhotos] = useState<ImagePicker.ImagePickerAsset[]>([]);
  const [savedPhotoCount, setSavedPhotoCount] = useState(0);
  const captureConfig = settings.runtime.surfaceConfig.capture;
  const sections = orderedSections(captureConfig.sectionOrder);

  const title = value.trim().split('\n')[0]?.slice(0, 70) || (photos.length ? `${catalog.activeManifest.label} photo` : `${catalog.activeManifest.label} note`);
  const hasLocalGraph = Boolean(db);
  const destinationHint = captureConfig.destinationHint || (hasLocalGraph
    ? `Writes to ${catalog.activeManifest.label} local graph with no network dependency.`
    : 'No local graph yet. Capture is kept in-session for this phase.');

  useEffect(() => {
    const requested = typeof params.type === 'string' ? params.type : '';
    const configured = types.find(([name]) => name.toLowerCase() === (requested || captureConfig.defaultType).toLowerCase())?.[0];
    if (configured) setType(configured);
  }, [captureConfig.defaultType, params.type]);

  const pickPhoto = async (source: 'camera' | 'library') => {
    setNotice('');
    const canUseCamera = source === 'camera' && Platform.OS !== 'web';
    if (canUseCamera) {
      const permission = await ImagePicker.requestCameraPermissionsAsync();
      if (!permission.granted) {
        setNotice('Camera permission was not granted.');
        return;
      }
    } else {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        setNotice('Photo library permission was not granted.');
        return;
      }
    }

    const result = canUseCamera
      ? await ImagePicker.launchCameraAsync({ quality: 0.82, allowsEditing: false })
      : await ImagePicker.launchImageLibraryAsync({ quality: 0.82, allowsEditing: false, mediaTypes: ['images'] });
    if (result.canceled) {
      return;
    }
    setType('Photo');
    setPhotos((current) => [...current, ...result.assets]);
    setSavedPhotoCount(0);
    setSaved(false);
  };

  const handleSave = async () => {
    if (!value.trim() && photos.length === 0) {
      return;
    }

    if (!db) {
      setSavedPhotoCount(photos.length);
      setSaved(true);
      return;
    }

    setSaving(true);
    const now = new Date().toISOString();
    const recordId = `capture-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const photoCount = photos.length;

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
            attachments: photos.map((photo) => ({
              kind: 'photo',
              uri: photo.uri,
              width: photo.width,
              height: photo.height,
              fileName: photo.fileName ?? null,
              assetId: photo.assetId ?? null,
              mimeType: photo.mimeType ?? null,
            })),
            targetRecordId: params.targetRecordId ?? null,
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
          relations: params.targetRecordId ? [{ name: 'attachment_for', target_id: params.targetRecordId }] : [],
        }
      );
      setSaved(true);
      setSavedPhotoCount(photoCount);
      setValue('');
      setPhotos([]);
    } finally {
      setSaving(false);
    }
  };

  const renderSection = (section: CaptureSection) => {
    switch (section) {
      case 'hero':
        return captureConfig.showHero ? (
          <View key={section}>
            <View style={styles.contextBar}>
              <View><Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / CAPTURE</Text><Text style={[styles.context, { color: theme.colors.muted }]}>Fast inbox · source preserved</Text></View>
              <Pill tone="moss">{catalog.activeManifest.label} graph</Pill>
            </View>
            <PageHeader
              eyebrow="Inbox first"
              title="Capture anything."
              subtitle="Save the raw thing now. LifeOS keeps the source, classifies later, and connects it to meals, pantry, shopping or any future domain."
            />
          </View>
        ) : null;
      case 'typePicker':
        return captureConfig.showTypePicker ? (
          <ScrollView key={section} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.types}>
            {types.map(([name, icon]) => (
              <Pressable
                key={name}
                accessibilityRole="button"
                accessibilityState={{ selected: type === name }}
                onPress={() => {
                  setType(name);
                  setSaved(false);
                  setSavedPhotoCount(0);
                }}
                style={[styles.type, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, type === name && [styles.typeActive, { backgroundColor: theme.colors.ink }]]}
              >
                <Text>{icon}</Text>
                <Text style={[styles.typeText, { color: theme.colors.ink }, type === name && { color: theme.colors.paper }]}>{name}</Text>
              </Pressable>
            ))}
          </ScrollView>
        ) : null;
      case 'editor':
        return captureConfig.showEditor ? (
          <Card key={section} style={styles.editorCard}>
            <View style={styles.editorTop}>
              <Pill tone="moss">{type.toUpperCase()}</Pill>
              <Text style={[styles.destination, { color: theme.colors.muted }]}>→ {hasLocalGraph ? `${catalog.activeManifest.label} graph` : 'Food inbox (preview)'}</Text>
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
              placeholderTextColor={theme.colors.muted}
              style={[styles.input, { color: theme.colors.ink }]}
              textAlignVertical="top"
            />
            {captureConfig.showAttachments ? (
              <View style={[styles.attachments, { borderTopColor: theme.colors.line }]}>
                <Pressable accessibilityRole="button" onPress={() => void pickPhoto('camera')} style={({ pressed }) => [styles.attachmentButton, pressed && styles.pressed]}>
                  <Text style={[styles.attachment, { color: theme.colors.ink }]}>▧ Camera</Text>
                </Pressable>
                <Pressable accessibilityRole="button" onPress={() => void pickPhoto('library')} style={({ pressed }) => [styles.attachmentButton, pressed && styles.pressed]}>
                  <Text style={[styles.attachment, { color: theme.colors.ink }]}>▧ Library</Text>
                </Pressable>
                <Text style={[styles.attachment, { color: theme.colors.muted }]}>⌁ Link</Text>
                <Text style={[styles.attachment, { color: theme.colors.muted }]}>◖ Record</Text>
              </View>
            ) : null}
            {photos.length ? (
              <View style={styles.photoGrid}>
                {photos.map((photo) => (
                  <View key={photo.uri} style={[styles.photoPreview, { borderColor: theme.colors.line }]}>
                    <Image source={{ uri: photo.uri }} style={styles.photoImage} />
                    <Text style={[styles.photoMeta, { color: theme.colors.muted }]} numberOfLines={1}>{photo.fileName || `${photo.width}×${photo.height}`}</Text>
                  </View>
                ))}
              </View>
            ) : null}
          </Card>
        ) : null;
      case 'routeCard':
        return captureConfig.showRouteCard ? (
          <Card key={section} tone="blue" style={styles.routeCard}>
            <Text style={[styles.routeTitle, { color: theme.colors.ink }]}>What happens next</Text>
            <Text style={[styles.routeBody, { color: theme.colors.muted }]}>Classify → preserve source → link relations → show in Chat citations.</Text>
          </Card>
        ) : null;
      default:
        return null;
    }
  };

  return (
    <Page>
      <ScrollView keyboardShouldPersistTaps="handled">
        <View style={sharedStyles.content}>
          {sections.map(renderSection)}
          <Text style={[styles.hint, { color: theme.colors.muted }]}>
            {destinationHint}
          </Text>
          {params.targetRecordId ? <Text style={[styles.hint, { color: theme.colors.moss }]}>Will link to record {params.targetRecordId}.</Text> : null}
          {notice ? <Text accessibilityLiveRegion="polite" style={[styles.notice, { color: theme.colors.moss }]}>{notice}</Text> : null}
          {saved ? (
            <Card tone="moss" style={styles.success}>
              <Text style={styles.successTitle}>Captured</Text>
              <Text style={sharedStyles.muted}>Stored in {hasLocalGraph ? `${catalog.activeManifest.label} graph` : 'session fallback'}{savedPhotoCount ? ` with ${savedPhotoCount} photo${savedPhotoCount === 1 ? '' : 's'}` : ''}.</Text>
            </Card>
          ) : null}
          <View style={styles.actions}>
            <ActionButton label="Save capture" disabled={(!value.trim() && photos.length === 0) || saving} onPress={() => void handleSave()} />
            <ActionButton label="Cancel" quiet onPress={() => router.back()} />
          </View>
        </View>
      </ScrollView>
    </Page>
  );
}

const styles = StyleSheet.create({
  contextBar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  context: { color: colors.muted, fontSize: 12, marginTop: 3 },
  types: { gap: 9, marginBottom: 15 },
  type: { minWidth: 76, paddingHorizontal: 14, paddingVertical: 11, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.line, backgroundColor: '#E9E8DF', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  typeActive: { backgroundColor: colors.ink },
  typeText: { color: colors.ink, fontSize: 12, fontWeight: '800' },
  typeTextActive: { color: '#FFF' },
  editorCard: { minHeight: 260 },
  editorTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  destination: { color: colors.muted, fontSize: 11, fontWeight: '700' },
  input: { flex: 1, minHeight: 150, color: colors.ink, fontSize: 18, lineHeight: 27, paddingVertical: 18 },
  attachments: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, paddingTop: 13, flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  attachmentButton: { minHeight: 38, borderRadius: radius.pill, backgroundColor: colors.canvas, paddingHorizontal: 12, justifyContent: 'center' },
  attachment: { color: colors.muted, fontSize: 12, fontWeight: '700' },
  photoGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 12 },
  photoPreview: { width: 116, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, overflow: 'hidden', backgroundColor: colors.paper },
  photoImage: { width: '100%', height: 92 },
  photoMeta: { color: colors.muted, fontSize: 10, padding: 7 },
  routeCard: { marginTop: 12 },
  routeTitle: { color: colors.ink, fontSize: 14, fontWeight: '900' },
  routeBody: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 4 },
  hint: { color: colors.muted, fontSize: 11, lineHeight: 17, margin: 12 },
  notice: { color: colors.moss, fontSize: 12, lineHeight: 18, marginHorizontal: 12, fontWeight: '800' },
  success: { marginTop: 6 },
  successTitle: { color: colors.ink, fontSize: 14, fontWeight: '800', marginBottom: 4 },
  actions: { flexDirection: 'row', gap: 9, marginTop: 18 },
  pressed: { opacity: 0.7 },
});
