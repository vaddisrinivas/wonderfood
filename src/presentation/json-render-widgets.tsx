import type { ComponentRegistry, ComponentRenderProps } from '@json-render/react-native';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { sendChatMessage } from '@/src/chat/client';
import type { ChatMessage, ChatThread } from '@/src/chat/types';
import {
  activateApprovedAppPackageChange,
  getActiveAppPackage,
  previewAppPackageChange,
  type AppPackageChangePreview,
  type AppPackageChangeRequest,
} from '@/src/db/app-package-registry';
import { useLifeOSDatabase } from '@/src/db/provider';
import { buildSafePackageChangeRequest } from '@/src/domain/package-change-templates';
import {
  getLifeOSHealthStatus,
  openLifeOSHealthSettings,
  requestLifeOSHealthPermissions,
  type HealthConnectStatus,
} from '@/src/health/connect';

type WidgetProps = {
  widget?: string;
  title?: string;
  subtitle?: string;
  prompt?: string;
  suggestions?: string[];
  body?: string;
  author?: string;
  url?: string;
  imageUrl?: string;
  items?: unknown[];
  options?: unknown[];
  columns?: unknown[];
  points?: unknown[];
  permissions?: unknown[];
  status?: string;
};

const DEFAULT_PROMPTS = [
  'Plan dinner from food that expires first.',
  'Add milk and cilantro to my shopping list.',
  'Create a better pantry table.',
  'Make this app calmer and less dense.',
];

function text(value: unknown, fallback = '') {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function list(value: unknown, fallback: string[]) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : fallback;
}

function rows(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value)
    ? value.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object' && !Array.isArray(item))
    : [];
}

function label(value: unknown, fallback = 'Item') {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const raw = value as Record<string, unknown>;
    return text(raw.title, text(raw.label, text(raw.name, text(raw.permission, text(raw.id, fallback)))));
  }
  return fallback;
}

function detail(value: unknown, fallback = '') {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const raw = value as Record<string, unknown>;
    return text(raw.subtitle, text(raw.body, text(raw.detail, text(raw.reason, fallback))));
  }
  return fallback;
}

function WidgetShell({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>{title}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      {children}
    </View>
  );
}

function Bubble({ message }: { message: ChatMessage }) {
  const assistant = message.role === 'assistant';
  return (
    <View style={[styles.bubble, assistant ? styles.assistantBubble : styles.userBubble]}>
      <Text style={assistant ? styles.assistantText : styles.userText}>{message.text}</Text>
      {message.answer?.recordCards?.length ? (
        <View style={styles.sources}>
          {message.answer.recordCards.slice(0, 3).map((record) => (
            <Text key={record.id} style={styles.sourceText}>• {record.title} · {record.detail}</Text>
          ))}
        </View>
      ) : null}
    </View>
  );
}

function AssistantChatWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const db = useLifeOSDatabase();
  const [thread, setThread] = useState<ChatThread | null>(null);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const suggestions = useMemo(() => list(props.suggestions, DEFAULT_PROMPTS), [props.suggestions]);

  const submit = useCallback(async (raw: string) => {
    const value = raw.trim();
    if (!value || busy) return;
    setInput('');
    setBusy(true);
    setError(null);
    try {
      const result = await sendChatMessage({
        db,
        text: value,
        domainId: 'food',
        conversationId: thread?.id,
        actor: 'mobile-json-render',
      });
      setThread(result.thread);
      if (result.serverError) setError(result.serverError);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Wonder chat failed.');
    } finally {
      setBusy(false);
    }
  }, [busy, db, thread?.id]);

  const messages = thread?.messages ?? [];

  return (
    <WidgetShell
      title={text(props.title, 'Ask Wonder')}
      subtitle={text(props.subtitle, 'Food assistant, app editor, and safe proposal surface.')}
    >
      <ScrollView style={styles.chatLog} contentContainerStyle={styles.chatLogContent}>
        {messages.length ? messages.map((message) => <Bubble key={message.id} message={message} />) : (
          <View style={styles.emptyChat}>
            <Text style={styles.emptyTitle}>What should food do next?</Text>
            <Text style={styles.emptyCopy}>Ask for dinner, pantry cleanup, shopping, or app changes. Wonder answers from local records when live AI is unavailable.</Text>
          </View>
        )}
        {busy ? <ActivityIndicator color="#2F7448" /> : null}
      </ScrollView>
      {error ? <Text style={styles.warning}>{error}</Text> : null}
      <View style={styles.suggestions}>
        {suggestions.slice(0, 4).map((suggestion) => (
          <Pressable key={suggestion} style={styles.suggestion} onPress={() => submit(suggestion)}>
            <Text style={styles.suggestionText}>{suggestion}</Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.inputRow}>
        <TextInput
          value={input}
          onChangeText={setInput}
          placeholder={text(props.prompt, 'Ask Wonder…')}
          placeholderTextColor="#8A8172"
          style={styles.input}
          multiline
        />
        <Pressable style={[styles.send, busy ? styles.disabled : null]} onPress={() => submit(input)} disabled={busy}>
          <Text style={styles.sendText}>{busy ? '…' : 'Send'}</Text>
        </Pressable>
      </View>
    </WidgetShell>
  );
}

function HealthConnectWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const [status, setStatus] = useState<HealthConnectStatus | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    setBusy(true);
    try {
      setStatus(await getLifeOSHealthStatus());
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const askPermission = useCallback(async () => {
    setBusy(true);
    try {
      setStatus(await requestLifeOSHealthPermissions());
    } finally {
      setBusy(false);
    }
  }, []);

  return (
    <WidgetShell
      title={text(props.title, 'Health Connect')}
      subtitle={text(props.subtitle, 'Android health permissions and food-health context.')}
    >
      <View style={styles.statusPill}>
        <Text style={styles.statusText}>{status?.availability ?? 'checking'}</Text>
      </View>
      <Text style={styles.bodyText}>{status?.message ?? 'Checking Health Connect on this device…'}</Text>
      <Text style={styles.bodyText}>{status?.granted.length ? `${status.granted.length} permissions granted` : 'No granted permissions detected yet.'}</Text>
      <View style={styles.buttonRow}>
        <Pressable style={styles.secondaryButton} onPress={refresh} disabled={busy}>
          <Text style={styles.secondaryButtonText}>Refresh</Text>
        </Pressable>
        <Pressable style={styles.primaryButton} onPress={askPermission} disabled={busy}>
          <Text style={styles.primaryButtonText}>Allow access</Text>
        </Pressable>
        <Pressable style={styles.secondaryButton} onPress={() => void openLifeOSHealthSettings()}>
          <Text style={styles.secondaryButtonText}>System</Text>
        </Pressable>
      </View>
    </WidgetShell>
  );
}

function SchemaEditorWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const db = useLifeOSDatabase();
  const [prompt, setPrompt] = useState(text(props.prompt, 'Add a notes table with a cute card list'));
  const [preview, setPreview] = useState<AppPackageChangePreview | null>(null);
  const [request, setRequest] = useState<AppPackageChangeRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const buildPreview = useCallback(async () => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (!db) throw new Error('Database is not ready yet.');
      const active = await getActiveAppPackage(db);
      if (!active) throw new Error('No active app package yet.');
      const nextRequest = buildSafePackageChangeRequest(active, prompt);
      const nextPreview = await previewAppPackageChange(db, nextRequest);
      setRequest(nextRequest);
      setPreview(nextPreview);
      setMessage(nextPreview.status === 'valid' ? 'Preview ready. Review, then approve.' : 'Preview blocked. Nothing changed.');
    } catch (err) {
      setPreview(null);
      setRequest(null);
      setError(err instanceof Error ? err.message : 'Package preview failed.');
    } finally {
      setBusy(false);
    }
  }, [db, prompt]);

  const applyPreview = useCallback(async () => {
    if (!request || !preview?.packageHash || preview.status !== 'valid') return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (!db) throw new Error('Database is not ready yet.');
      const applied = await activateApprovedAppPackageChange(db, request, {
        schemaVersion: 'wonder.package-change-approval.v1',
        approved: true,
        requestHash: preview.requestHash,
        packageHash: preview.packageHash,
        approvedBy: 'mobile-package-editor',
        approvedAt: new Date().toISOString(),
      });
      setMessage(`Applied ${applied.id}@${applied.version}. Reopen this screen if it does not refresh immediately.`);
      setPreview(null);
      setRequest(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Package apply failed.');
    } finally {
      setBusy(false);
    }
  }, [db, preview, request]);

  return (
    <WidgetShell
      title={text(props.title, 'AI package editor')}
      subtitle={text(props.subtitle, 'Describe a table or screen change. Wonder previews a safe package diff before it can apply.')}
    >
      <TextInput
        value={prompt}
        onChangeText={setPrompt}
        placeholder="Example: add a family recipes table"
        placeholderTextColor="#8A8172"
        style={styles.editorInput}
        multiline
      />
      <View style={styles.buttonRow}>
        <Pressable style={[styles.primaryButton, busy ? styles.disabled : null]} onPress={buildPreview} disabled={busy}>
          <Text style={styles.primaryButtonText}>{busy ? 'Checking…' : 'Preview change'}</Text>
        </Pressable>
        {preview?.status === 'valid' ? (
          <Pressable style={[styles.secondaryButton, busy ? styles.disabled : null]} onPress={applyPreview} disabled={busy}>
            <Text style={styles.secondaryButtonText}>Approve & apply</Text>
          </Pressable>
        ) : null}
      </View>
      {message ? <Text style={styles.success}>{message}</Text> : null}
      {error ? <Text style={styles.warning}>{error}</Text> : null}
      {preview ? (
        <View style={styles.previewBox}>
          <Text style={styles.previewTitle}>{preview.status === 'valid' ? 'Safe package diff' : 'Blocked diff'}</Text>
          <Text style={styles.previewText}>Request {shortHash(preview.requestHash)}</Text>
          {preview.packageHash ? <Text style={styles.previewText}>Package {shortHash(preview.packageHash)}</Text> : null}
          {request ? <Text style={styles.previewText}>{request.patch.length} patch steps · hidden writes: no</Text> : null}
          {preview.errors.map((item) => <Text key={item} style={styles.warning}>• {item}</Text>)}
        </View>
      ) : (
        <Text style={styles.bodyText}>V1 supports safe app-package patches for new tables, views, and JSON-render screens. Native packages and dependency pins stay locked.</Text>
      )}
    </WidgetShell>
  );
}

function shortHash(value: string) {
  return value.length > 18 ? `${value.slice(0, 14)}…${value.slice(-4)}` : value;
}

function WidgetCatalogWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const widgets = [
    'Assistant chat',
    'Post cards',
    'Polls',
    'Link previews',
    'Feeds',
    'Kanban boards',
    'Charts',
    'Media',
    'Maps',
    'Permissions',
    'Provider status',
    'Theme preview',
    'Health Connect',
    'Record lists',
    'Metrics',
    'Actions',
    'Text cards',
    'Package editor',
  ];
  return (
    <WidgetShell title={text(props.title, 'Widget catalog')} subtitle={text(props.subtitle, 'The safe building blocks JSON Render can place on screens today.')}>
      <View style={styles.catalogGrid}>
        {widgets.map((item) => (
          <View key={item} style={styles.catalogItem}>
            <Text style={styles.catalogText}>{item}</Text>
          </View>
        ))}
      </View>
    </WidgetShell>
  );
}

function PostCardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  return (
    <WidgetShell title={text(props.title, 'Post')} subtitle={text(props.subtitle, text(props.author, 'Wonder'))}>
      <Text style={styles.bodyText}>{text(props.body, 'A package-defined post, note, update, or announcement.')}</Text>
      {props.url ? <Text style={styles.linkText}>{text(props.url)}</Text> : null}
    </WidgetShell>
  );
}

function PollCardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const [selected, setSelected] = useState<string | null>(null);
  const options = rows(props.options);
  return (
    <WidgetShell title={text(props.title, 'Poll')} subtitle={text(props.subtitle, 'Choose one. Stored action wiring comes from package proposals.')}>
      {(options.length ? options : [{ label: 'Yes' }, { label: 'No' }]).map((option) => {
        const optionLabel = label(option);
        return (
          <Pressable key={optionLabel} style={[styles.pollOption, selected === optionLabel ? styles.pollSelected : null]} onPress={() => setSelected(optionLabel)}>
            <Text style={styles.pollText}>{optionLabel}</Text>
            <Text style={styles.pollMeta}>{selected === optionLabel ? 'Selected' : detail(option, 'Tap to choose')}</Text>
          </Pressable>
        );
      })}
    </WidgetShell>
  );
}

function LinkPreviewWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const host = (() => {
    try {
      return props.url ? new URL(text(props.url)).hostname.replace(/^www\./, '') : 'link';
    } catch {
      return 'link';
    }
  })();
  return (
    <WidgetShell title={text(props.title, 'Link preview')} subtitle={host}>
      <Text style={styles.bodyText}>{text(props.subtitle, 'A safe preview surface for YouTube, docs, recipes, posts, and references.')}</Text>
      {props.url ? <Text style={styles.linkText}>{text(props.url)}</Text> : null}
    </WidgetShell>
  );
}

function FeedListWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const items = rows(props.items);
  return (
    <WidgetShell title={text(props.title, 'Feed')} subtitle={text(props.subtitle, 'Posts, links, updates, and activity in one stream.')}>
      {(items.length ? items : [{ title: 'No feed items yet', subtitle: 'Ask Wonder to add posts, links, or updates.' }]).slice(0, 8).map((item) => (
        <View key={label(item)} style={styles.feedItem}>
          <Text style={styles.feedTitle}>{label(item)}</Text>
          <Text style={styles.feedDetail}>{detail(item)}</Text>
        </View>
      ))}
    </WidgetShell>
  );
}

function KanbanBoardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const columns = rows(props.columns);
  return (
    <WidgetShell title={text(props.title, 'Board')} subtitle={text(props.subtitle, 'Generic grouped work, meals, projects, or approvals.')}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.board}>
        {(columns.length ? columns : [{ title: 'Ideas', items: [{ title: 'Plan dinner' }] }, { title: 'Next', items: [{ title: 'Buy cilantro' }] }]).map((column) => (
          <View key={label(column, 'Column')} style={styles.boardColumn}>
            <Text style={styles.boardTitle}>{label(column, 'Column')}</Text>
            {rows(column.items).slice(0, 5).map((item) => (
              <View key={label(item)} style={styles.boardCard}>
                <Text style={styles.boardCardText}>{label(item)}</Text>
              </View>
            ))}
          </View>
        ))}
      </ScrollView>
    </WidgetShell>
  );
}

function ChartBlockWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const points = rows(props.points);
  const values = (points.length ? points : [{ label: 'A', value: 6 }, { label: 'B', value: 10 }, { label: 'C', value: 4 }])
    .map((point) => ({ label: label(point), value: typeof point.value === 'number' ? point.value : Number(point.value ?? 0) }))
    .filter((point) => Number.isFinite(point.value));
  const max = Math.max(1, ...values.map((point) => point.value));
  return (
    <WidgetShell title={text(props.title, 'Chart')} subtitle={text(props.subtitle, 'Config-driven bars for budgets, habits, inventory, or signals.')}>
      <View style={styles.chart}>
        {values.slice(0, 8).map((point) => (
          <View key={point.label} style={styles.chartRow}>
            <Text style={styles.chartLabel}>{point.label}</Text>
            <View style={styles.chartTrack}><View style={[styles.chartFill, { width: `${Math.max(8, (point.value / max) * 100)}%` }]} /></View>
          </View>
        ))}
      </View>
    </WidgetShell>
  );
}

function MediaBlockWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  return (
    <WidgetShell title={text(props.title, 'Media')} subtitle={text(props.subtitle, 'Image, audio, and video slots declared by package config.')}>
      <View style={styles.mediaBox}>
        <Text style={styles.mediaGlyph}>▶︎</Text>
        <Text style={styles.bodyText}>{text(props.body, 'Attach or preview media here.')}</Text>
      </View>
    </WidgetShell>
  );
}

function MapBlockWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  return (
    <WidgetShell title={text(props.title, 'Map')} subtitle={text(props.subtitle, 'Location-aware surfaces without custom app code.')}>
      <View style={styles.mapBox}>
        <Text style={styles.mapPin}>⌖</Text>
        <Text style={styles.bodyText}>{text(props.body, 'Map provider hooks can render stores, trips, homes, routes, or field work.')}</Text>
      </View>
    </WidgetShell>
  );
}

function PermissionCardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const permissions = rows(props.permissions);
  return (
    <WidgetShell title={text(props.title, 'Permissions')} subtitle={text(props.subtitle, 'Native capabilities explained before request.')}>
      {(permissions.length ? permissions : [{ title: 'Health Connect', subtitle: 'Used only for food-health context.' }]).map((permission) => (
        <View key={label(permission)} style={styles.permissionRow}>
          <Text style={styles.permissionTitle}>{label(permission)}</Text>
          <Text style={styles.permissionDetail}>{detail(permission, 'Required by this package feature.')}</Text>
        </View>
      ))}
    </WidgetShell>
  );
}

function ProviderStatusWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  return (
    <WidgetShell title={text(props.title, 'Sync')} subtitle={text(props.subtitle, 'Provider sync should feel invisible until attention is needed.')}>
      <View style={styles.statusPill}>
        <Text style={styles.statusText}>{text(props.status, 'Ready')}</Text>
      </View>
      <Text style={styles.bodyText}>{text(props.body, 'Local data is primary. Notion and Sheets writes require verification before success.')}</Text>
    </WidgetShell>
  );
}

function ThemePreviewWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  return (
    <WidgetShell title={text(props.title, 'Theme')} subtitle={text(props.subtitle, 'Package-level design tokens for generated apps.')}>
      <View style={styles.swatches}>
        {['#2F7448', '#F3B15E', '#7B4E8A', '#B9DCE8', '#241C16'].map((color) => (
          <View key={color} style={[styles.swatch, { backgroundColor: color }]} />
        ))}
      </View>
    </WidgetShell>
  );
}

export const JSON_RENDER_WIDGET_REGISTRY: ComponentRegistry = {
  AssistantChatWidget,
  HealthConnectWidget,
  SchemaEditorWidget,
  WidgetCatalogWidget,
  PostCardWidget,
  PollCardWidget,
  LinkPreviewWidget,
  FeedListWidget,
  KanbanBoardWidget,
  ChartBlockWidget,
  MediaBlockWidget,
  MapBlockWidget,
  PermissionCardWidget,
  ProviderStatusWidget,
  ThemePreviewWidget,
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 24,
    padding: 16,
    gap: 12,
    shadowColor: '#271D14',
    shadowOpacity: 0.08,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 2,
  },
  title: { color: '#241C16', fontSize: 22, fontWeight: '800' },
  subtitle: { color: '#6D6257', fontSize: 14, lineHeight: 20 },
  bodyText: { color: '#4E463E', fontSize: 14, lineHeight: 20 },
  chatLog: { maxHeight: 420 },
  chatLogContent: { gap: 10, paddingBottom: 4 },
  emptyChat: { backgroundColor: '#F6F1E8', borderRadius: 18, padding: 16, gap: 6 },
  emptyTitle: { color: '#241C16', fontSize: 18, fontWeight: '800' },
  emptyCopy: { color: '#6D6257', fontSize: 14, lineHeight: 20 },
  bubble: { borderRadius: 18, padding: 12, gap: 8 },
  assistantBubble: { backgroundColor: '#F6F1E8', alignSelf: 'stretch' },
  userBubble: { backgroundColor: '#2F7448', alignSelf: 'flex-end', maxWidth: '88%' },
  assistantText: { color: '#241C16', fontSize: 15, lineHeight: 21 },
  userText: { color: '#FFFFFF', fontSize: 15, lineHeight: 21 },
  sources: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#D8CFC2', paddingTop: 8, gap: 4 },
  sourceText: { color: '#6D6257', fontSize: 12, lineHeight: 17 },
  warning: { color: '#9A4B2E', fontSize: 12 },
  success: { color: '#2F7448', fontSize: 12, fontWeight: '800' },
  editorInput: {
    minHeight: 84,
    borderRadius: 18,
    backgroundColor: '#F6F1E8',
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: '#241C16',
    fontSize: 15,
    lineHeight: 21,
  },
  previewBox: { borderRadius: 18, backgroundColor: '#F6F1E8', padding: 14, gap: 6 },
  previewTitle: { color: '#241C16', fontSize: 16, fontWeight: '900' },
  previewText: { color: '#6D6257', fontSize: 12, fontWeight: '700' },
  suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  suggestion: { backgroundColor: '#E4F1E8', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 },
  suggestionText: { color: '#2F7448', fontSize: 12, fontWeight: '700' },
  inputRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  input: {
    flex: 1,
    minHeight: 44,
    maxHeight: 110,
    borderRadius: 16,
    backgroundColor: '#F6F1E8',
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: '#241C16',
    fontSize: 15,
  },
  send: { backgroundColor: '#241C16', borderRadius: 16, paddingHorizontal: 14, paddingVertical: 12 },
  disabled: { opacity: 0.5 },
  sendText: { color: '#FFFFFF', fontWeight: '800' },
  statusPill: { alignSelf: 'flex-start', backgroundColor: '#E4F1E8', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 7 },
  statusText: { color: '#2F7448', fontWeight: '800' },
  buttonRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  primaryButton: { backgroundColor: '#2F7448', borderRadius: 14, paddingHorizontal: 12, paddingVertical: 10 },
  primaryButtonText: { color: '#FFFFFF', fontWeight: '800' },
  secondaryButton: { backgroundColor: '#F6F1E8', borderRadius: 14, paddingHorizontal: 12, paddingVertical: 10 },
  secondaryButtonText: { color: '#241C16', fontWeight: '800' },
  catalogGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  catalogItem: { backgroundColor: '#EFE6ED', borderRadius: 14, paddingHorizontal: 10, paddingVertical: 8 },
  catalogText: { color: '#3F2D42', fontWeight: '700', fontSize: 12 },
  linkText: { color: '#2F7448', fontSize: 13, fontWeight: '800' },
  pollOption: { borderRadius: 16, padding: 12, backgroundColor: '#F6F1E8', gap: 3 },
  pollSelected: { backgroundColor: '#E4F1E8', borderWidth: 1, borderColor: '#2F7448' },
  pollText: { color: '#241C16', fontSize: 15, fontWeight: '800' },
  pollMeta: { color: '#6D6257', fontSize: 12 },
  feedItem: { paddingVertical: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#D8CFC2' },
  feedTitle: { color: '#241C16', fontSize: 16, fontWeight: '800' },
  feedDetail: { color: '#6D6257', fontSize: 13, lineHeight: 18 },
  board: { gap: 10 },
  boardColumn: { width: 168, backgroundColor: '#F6F1E8', borderRadius: 18, padding: 10, gap: 8 },
  boardTitle: { color: '#241C16', fontWeight: '900' },
  boardCard: { backgroundColor: '#FFFFFF', borderRadius: 12, padding: 10 },
  boardCardText: { color: '#241C16', fontWeight: '700' },
  chart: { gap: 10 },
  chartRow: { gap: 5 },
  chartLabel: { color: '#6D6257', fontSize: 12, fontWeight: '800' },
  chartTrack: { height: 12, backgroundColor: '#F6F1E8', borderRadius: 999, overflow: 'hidden' },
  chartFill: { height: 12, backgroundColor: '#2F7448', borderRadius: 999 },
  mediaBox: { minHeight: 112, borderRadius: 18, backgroundColor: '#F6F1E8', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 16 },
  mediaGlyph: { color: '#241C16', fontSize: 32, fontWeight: '900' },
  mapBox: { minHeight: 112, borderRadius: 18, backgroundColor: '#E8F4F5', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 16 },
  mapPin: { color: '#2F7448', fontSize: 32, fontWeight: '900' },
  permissionRow: { borderRadius: 16, backgroundColor: '#F6F1E8', padding: 12, gap: 4 },
  permissionTitle: { color: '#241C16', fontWeight: '900' },
  permissionDetail: { color: '#6D6257', fontSize: 13, lineHeight: 18 },
  swatches: { flexDirection: 'row', gap: 8 },
  swatch: { width: 42, height: 42, borderRadius: 14 },
});
