import type { ComponentRegistry, ComponentRenderProps } from '@json-render/react-native';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Linking, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

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
import type { ProviderSyncStatus } from '@/src/db/provider-status';

type WidgetProps = {
  widget?: string;
  title?: string;
  subtitle?: string;
  prompt?: string;
  placeholder?: string;
  examples?: unknown[];
  suggestions?: string[];
  body?: string;
  author?: string;
  url?: string;
  imageUrl?: string;
  items?: unknown[];
  options?: unknown[];
  columns?: unknown[];
  fields?: unknown[];
  events?: unknown[];
  points?: unknown[];
  permissions?: unknown[];
  provider?: string;
  providerStatus?: ProviderSyncStatus;
  status?: string;
  badge?: string;
  cta?: string;
  homes?: unknown[];
  steps?: unknown[];
  actions?: unknown[];
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

function permissionLabel(value: Record<string, unknown>): string {
  const explicit = text(value.title, text(value.label, text(value.name)));
  if (explicit) return explicit;
  const rawPermission = text(value.permission, text(value.id));
  const normalized = rawPermission
    .replace(/^android\.permission\.health\./, '')
    .replace(/^expo-image-picker:/, '')
    .replace(/^expo-/, '')
    .replace(/^health-connect-/, '')
    .replace(/_/g, ' ')
    .replace(/-/g, ' ')
    .toLowerCase();
  if (!normalized) return 'Permission';
  return normalized.replace(/\b\w/g, (char) => char.toUpperCase());
}

function permissionMeta(value: Record<string, unknown>): string {
  const platform = text(value.platform, 'app');
  const required = value.required === true ? 'required' : 'optional';
  return `${platform} · ${required}`;
}

function actionRoute(value: Record<string, unknown>): string {
  return text(value.route, text(value.path));
}

function actionUrl(value: Record<string, unknown>): string {
  return text(value.url, text(value.href, text(value.deeplink)));
}

function fieldKey(value: Record<string, unknown>, index: number): string {
  return text(value.id, text(value.name, label(value, `field_${index}`))).toLowerCase().replace(/[^a-z0-9]+/g, '_');
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
      subtitle={text(props.subtitle, 'Optional Android health context for food decisions. You choose what is shared.')}
    >
      <View style={styles.statusPill}>
        <Text style={styles.statusText}>{status?.availability ?? 'checking'}</Text>
      </View>
      <Text style={styles.bodyText}>{status?.message ?? 'Checking Health Connect on this device…'}</Text>
      <Text style={styles.bodyText}>{status?.granted.length ? `${status.granted.length} permissions ready` : 'No health permissions enabled yet.'}</Text>
      <View style={styles.buttonRow}>
        <Pressable style={styles.secondaryButton} onPress={refresh} disabled={busy}>
          <Text style={styles.secondaryButtonText}>Check again</Text>
        </Pressable>
        <Pressable style={styles.primaryButton} onPress={askPermission} disabled={busy}>
          <Text style={styles.primaryButtonText}>Choose access</Text>
        </Pressable>
        <Pressable style={styles.secondaryButton} onPress={() => void openLifeOSHealthSettings()}>
          <Text style={styles.secondaryButtonText}>Android settings</Text>
        </Pressable>
      </View>
    </WidgetShell>
  );
}

function SchemaEditorWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const db = useLifeOSDatabase();
  const [prompt, setPrompt] = useState(text(props.prompt, 'Add a notes table with a cute card list'));
  const examples = rows(props.examples).map((item) => ({
    title: label(item),
    prompt: text(item.prompt, label(item)),
    detail: detail(item),
  })).filter((item) => item.prompt.length > 0);
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
        placeholder={text(props.placeholder, 'Example: add a family recipes table')}
        placeholderTextColor="#8A8172"
        style={styles.editorInput}
        multiline
      />
      {examples.length ? (
        <View style={styles.exampleGrid}>
          {examples.slice(0, 6).map((example) => (
            <Pressable key={example.prompt} style={styles.exampleChip} onPress={() => setPrompt(example.prompt)}>
              <Text style={styles.exampleTitle}>{example.title}</Text>
              {example.detail ? <Text style={styles.exampleDetail}>{example.detail}</Text> : null}
            </Pressable>
          ))}
        </View>
      ) : null}
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
  const widgets = rows(props.items).map((item) => label(item)).filter(Boolean);
  const defaultWidgets = [
    'Assistant chat',
    'Post cards',
    'Polls',
    'Link previews',
    'Feeds',
    'Kanban boards',
    'Charts',
    'Media',
    'Maps',
    'Forms',
    'Checklists',
    'Calendars',
    'Timelines',
    'Galleries',
    'Data tables',
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
        {(widgets.length ? widgets : defaultWidgets).map((item) => (
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
  const actions = rows(props.actions);
  return (
    <WidgetShell title={text(props.title, 'Post')} subtitle={text(props.subtitle, text(props.author, 'Wonder'))}>
      {props.badge ? <Text style={styles.softBadge}>{text(props.badge)}</Text> : null}
      <Text style={styles.bodyText}>{text(props.body, 'A package-defined post, note, update, or announcement.')}</Text>
      {props.url ? <Text style={styles.linkText}>{text(props.url)}</Text> : null}
      {actions.length ? (
        <View style={styles.buttonRow}>
          {actions.slice(0, 3).map((action) => (
            <View key={label(action)} style={styles.miniAction}>
              <Text style={styles.miniActionText}>{label(action)}</Text>
            </View>
          ))}
        </View>
      ) : null}
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
      <View style={styles.previewHero}>
        <Text style={styles.previewGlyph}>↗</Text>
        <Text style={styles.previewHost}>{host}</Text>
      </View>
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
          <View style={styles.feedDot} />
          <View style={styles.feedCopy}>
            <Text style={styles.feedTitle}>{label(item)}</Text>
            <Text style={styles.feedDetail}>{detail(item)}</Text>
          </View>
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

function FormCardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const fields = rows(props.fields);
  const fallback: Record<string, unknown>[] = [
    { label: 'Title', subtitle: 'Text', placeholder: 'What is this?' },
    { label: 'Notes', subtitle: 'Long text', placeholder: 'Add useful context…' },
    { label: 'Status', subtitle: 'Choice', placeholder: 'New, review, done…' },
  ];
  const formFields = (fields.length ? fields : fallback).slice(0, 8);
  const [values, setValues] = useState<Record<string, string>>({});
  const [submitted, setSubmitted] = useState(false);
  return (
    <WidgetShell title={text(props.title, 'Form')} subtitle={text(props.subtitle, 'Config-declared inputs. Writes must still go through proposals/actions.')}>
      {formFields.map((field, index) => {
        const key = fieldKey(field, index);
        const fieldType = text(field.type, detail(field, 'Field'));
        const multiline = /long|note|textarea|multi/i.test(fieldType);
        return (
        <View key={key} style={styles.formField}>
          <Text style={styles.formLabel}>{label(field)}</Text>
          <Text style={styles.formHint}>{fieldType}{field.required === true ? ' · Required' : ''}</Text>
          <TextInput
            style={[styles.formInput, multiline ? styles.formInputMultiline : null]}
            value={values[key] ?? ''}
            onChangeText={(next) => {
              setSubmitted(false);
              setValues((prev) => ({ ...prev, [key]: next }));
            }}
            placeholder={text(field.placeholder, `Enter ${label(field).toLowerCase()}`)}
            placeholderTextColor="#9A8D7D"
            multiline={multiline}
          />
        </View>
        );
      })}
      {submitted ? <Text style={styles.success}>Preview ready. Review before writing.</Text> : null}
      <Pressable style={styles.primaryButton} onPress={() => setSubmitted(true)}>
        <Text style={styles.primaryButtonText}>{text(props.body, text(props.cta, 'Preview action'))}</Text>
      </Pressable>
    </WidgetShell>
  );
}

function ChecklistCardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const items = rows(props.items);
  const [checked, setChecked] = useState<Record<string, boolean>>({});
  return (
    <WidgetShell title={text(props.title, 'Checklist')} subtitle={text(props.subtitle, 'Tasks, packing, QA, habits, recipes, or setup steps.')}>
      {(items.length ? items : [{ title: 'First step' }, { title: 'Second step' }, { title: 'Done' }]).slice(0, 10).map((item) => {
        const key = label(item);
        return (
          <Pressable key={key} style={styles.checkRow} onPress={() => setChecked((prev) => ({ ...prev, [key]: !prev[key] }))}>
            <Text style={[styles.checkBox, checked[key] ? styles.checkBoxOn : null]}>{checked[key] ? '✓' : ''}</Text>
            <View style={styles.checkCopy}>
              <Text style={styles.checkTitle}>{key}</Text>
              {detail(item) ? <Text style={styles.checkDetail}>{detail(item)}</Text> : null}
            </View>
          </Pressable>
        );
      })}
    </WidgetShell>
  );
}

function CalendarBlockWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const events = rows(props.events);
  return (
    <WidgetShell title={text(props.title, 'Calendar')} subtitle={text(props.subtitle, 'Plans, bookings, reminders, routines, and schedules.')}>
      {(events.length ? events : [{ title: 'Dinner plan', subtitle: 'Tonight' }, { title: 'Shopping', subtitle: 'Tomorrow' }]).slice(0, 7).map((event) => (
        <View key={label(event)} style={styles.calendarRow}>
          <Text style={styles.calendarDate}>{text(event.date, text(event.when, 'Soon'))}</Text>
          <View style={styles.calendarCopy}>
            <Text style={styles.feedTitle}>{label(event)}</Text>
            <Text style={styles.feedDetail}>{detail(event)}</Text>
          </View>
        </View>
      ))}
    </WidgetShell>
  );
}

function TimelineBlockWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const items = rows(props.items);
  return (
    <WidgetShell title={text(props.title, 'Timeline')} subtitle={text(props.subtitle, 'History, provenance, milestones, trips, cases, or change logs.')}>
      {(items.length ? items : [{ title: 'Started', subtitle: 'Created from package config' }, { title: 'Next', subtitle: 'Ask Wonder to add events' }]).slice(0, 10).map((item) => (
        <View key={label(item)} style={styles.timelineRow}>
          <View style={styles.timelineDot} />
          <View style={styles.timelineCopy}>
            <Text style={styles.feedTitle}>{label(item)}</Text>
            <Text style={styles.feedDetail}>{detail(item, text(item.time, ''))}</Text>
          </View>
        </View>
      ))}
    </WidgetShell>
  );
}

function GalleryGridWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const items = rows(props.items);
  return (
    <WidgetShell title={text(props.title, 'Gallery')} subtitle={text(props.subtitle, 'Photos, media, assets, places, products, recipes, or memories.')}>
      <View style={styles.galleryGrid}>
        {(items.length ? items : [{ title: 'Image' }, { title: 'Clip' }, { title: 'Doc' }, { title: 'Audio' }]).slice(0, 8).map((item) => (
          <View key={label(item)} style={styles.galleryTile}>
            <Text style={styles.galleryGlyph}>{text(item.emoji, '◼︎')}</Text>
            <Text style={styles.galleryText}>{label(item)}</Text>
          </View>
        ))}
      </View>
    </WidgetShell>
  );
}

function DataTableWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const columns = rows(props.columns);
  const items = rows(props.items);
  const columnLabels = (columns.length ? columns.map((column) => label(column)) : ['Name', 'Status', 'Owner']).slice(0, 4);
  const tableRows = (items.length ? items : [{ name: 'Sample', status: 'Ready', owner: 'Wonder' }]).slice(0, 6);
  return (
    <WidgetShell title={text(props.title, 'Table')} subtitle={text(props.subtitle, 'Compact structured records without a custom screen.')}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        <View style={styles.table}>
          <View style={styles.tableRow}>
            {columnLabels.map((column) => <Text key={column} style={styles.tableHeader}>{column}</Text>)}
          </View>
          {tableRows.map((row, index) => (
            <View key={`${label(row)}-${index}`} style={styles.tableRow}>
              {columnLabels.map((column) => {
                const key = column.toLowerCase().replace(/\s+/g, '_');
                return <Text key={column} style={styles.tableCell}>{text(row[key], text(row[column], index === 0 ? label(row) : '—'))}</Text>;
              })}
            </View>
          ))}
        </View>
      </ScrollView>
    </WidgetShell>
  );
}

function PermissionCardWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const permissions = rows(props.permissions);
  return (
    <WidgetShell title={text(props.title, 'Permissions')} subtitle={text(props.subtitle, 'This app asks only when a package feature needs native access.')}>
      {(permissions.length ? permissions : [{ title: 'Health Connect', subtitle: 'Optional food-health context; you stay in control.' }]).map((permission) => (
        <View key={text(permission.id, permissionLabel(permission))} style={styles.permissionRow}>
          <View style={styles.permissionHeading}>
            <Text style={styles.permissionTitle}>{permissionLabel(permission)}</Text>
            <Text style={styles.permissionMeta}>{permissionMeta(permission)}</Text>
          </View>
          <Text style={styles.permissionDetail}>{text(permission.prompt, detail(permission, 'Used only for this package feature.'))}</Text>
        </View>
      ))}
    </WidgetShell>
  );
}

function ProviderStatusWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const router = useRouter();
  const props = element.props ?? {};
  const summary = props.providerStatus;
  const status = summary?.headline ?? text(props.status, 'Quietly ready');
  const body = summary?.detail ?? text(props.body, 'Local works first. Notion and Sheets stay invisible unless they need attention.');
  const attention = summary?.status === 'attention';
  const connected = summary?.connected ?? false;
  const homes = rows(props.homes);
  const steps = rows(props.steps);
  const actions = rows(props.actions);
  const runAction = useCallback((action: Record<string, unknown>) => {
    const route = actionRoute(action);
    if (route) {
      router.push(route as never);
      return;
    }
    const url = actionUrl(action);
    if (url) {
      void Linking.openURL(url);
    }
  }, [router]);
  return (
    <WidgetShell title={text(props.title, 'Sources')} subtitle={text(props.subtitle, 'Your data homes stay quiet until there is something useful to do.')}>
      <View style={[styles.statusPill, attention ? styles.statusPillAttention : null]}>
        <Text style={[styles.statusText, attention ? styles.statusTextAttention : null]}>{status}</Text>
      </View>
      <Text style={styles.bodyText}>{body}</Text>
      {homes.length ? (
        <View style={styles.sourceHomes}>
          {homes.slice(0, 4).map((home) => (
            <View key={label(home)} style={styles.sourceHome}>
              <Text style={styles.sourceHomeIcon}>{text(home.icon, '⌁')}</Text>
              <View style={styles.sourceHomeCopy}>
                <Text style={styles.sourceHomeTitle}>{label(home)}</Text>
                <Text style={styles.sourceHomeDetail}>{detail(home)}</Text>
              </View>
              <Text style={styles.sourceHomeState}>{text(home.status, 'Ready')}</Text>
            </View>
          ))}
        </View>
      ) : null}
      {summary ? (
        <View style={styles.providerStats}>
          <Text style={styles.providerStat}>{connected ? `${summary.linkCount} connected` : 'On-device'}</Text>
          <Text style={styles.providerStat}>{summary.pendingWrites + summary.inflightWrites ? `${summary.pendingWrites + summary.inflightWrites} syncing` : 'Synced'}</Text>
          <Text style={[styles.providerStat, attention ? styles.providerStatAttention : null]}>{summary.failedWrites ? `${summary.failedWrites} need help` : 'Healthy'}</Text>
        </View>
      ) : null}
      {steps.length ? (
        <View style={styles.providerSteps}>
          {steps.slice(0, 4).map((step, index) => (
            <View key={label(step)} style={styles.providerStep}>
              <Text style={styles.providerStepNumber}>{index + 1}</Text>
              <View style={styles.sourceHomeCopy}>
                <Text style={styles.providerStepTitle}>{label(step)}</Text>
                <Text style={styles.sourceHomeDetail}>{detail(step)}</Text>
              </View>
            </View>
          ))}
        </View>
      ) : null}
      {actions.length ? (
        <View style={styles.providerActions}>
          {actions.slice(0, 3).map((action) => (
            <Pressable key={label(action)} style={styles.providerAction} onPress={() => runAction(action)}>
              <Text style={styles.providerActionTitle}>{label(action)}</Text>
              <Text style={styles.providerActionDetail}>{detail(action, 'Ready when you are.')}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}
      {props.cta ? <Text style={styles.providerCta}>{text(props.cta)}</Text> : null}
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
  FormCardWidget,
  ChecklistCardWidget,
  CalendarBlockWidget,
  TimelineBlockWidget,
  GalleryGridWidget,
  DataTableWidget,
  PermissionCardWidget,
  ProviderStatusWidget,
  ThemePreviewWidget,
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFCF5',
    borderRadius: 20,
    padding: 14,
    gap: 12,
    shadowColor: '#271D14',
    shadowOpacity: 0.04,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 1,
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
  exampleGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  exampleChip: { maxWidth: '48%', borderRadius: 16, backgroundColor: '#EFE6ED', paddingHorizontal: 11, paddingVertical: 9, gap: 2 },
  exampleTitle: { color: '#3F2D42', fontSize: 12, fontWeight: '900' },
  exampleDetail: { color: '#6D6257', fontSize: 11, lineHeight: 15 },
  previewBox: { borderRadius: 18, backgroundColor: '#F6F1E8', padding: 14, gap: 6 },
  previewTitle: { color: '#241C16', fontSize: 16, fontWeight: '900' },
  previewText: { color: '#6D6257', fontSize: 12, fontWeight: '700' },
  softBadge: { alignSelf: 'flex-start', backgroundColor: '#FFF1B8', borderRadius: 999, color: '#7A5B00', fontSize: 12, fontWeight: '900', paddingHorizontal: 10, paddingVertical: 6 },
  miniAction: { backgroundColor: '#241C16', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 },
  miniActionText: { color: '#FFFFFF', fontSize: 12, fontWeight: '900' },
  previewHero: { minHeight: 76, borderRadius: 18, backgroundColor: '#E4F1E8', padding: 14, justifyContent: 'space-between' },
  previewGlyph: { color: '#2F7448', fontSize: 24, fontWeight: '900' },
  previewHost: { color: '#2F7448', fontSize: 12, fontWeight: '900' },
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
  statusPillAttention: { backgroundColor: '#F9E7D9' },
  statusText: { color: '#2F7448', fontWeight: '800' },
  statusTextAttention: { color: '#9A4B2E' },
  providerStats: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  providerStat: { backgroundColor: '#F6F1E8', borderRadius: 999, color: '#6D6257', fontSize: 12, fontWeight: '800', paddingHorizontal: 10, paddingVertical: 6 },
  providerStatAttention: { color: '#9A4B2E', backgroundColor: '#F9E7D9' },
  sourceHomes: { gap: 8 },
  sourceHome: { flexDirection: 'row', alignItems: 'center', gap: 10, borderRadius: 18, backgroundColor: '#F6F1E8', padding: 12 },
  sourceHomeIcon: { width: 32, height: 32, borderRadius: 12, backgroundColor: '#E4F1E8', color: '#2F7448', textAlign: 'center', lineHeight: 32, fontSize: 16, fontWeight: '900', overflow: 'hidden' },
  sourceHomeCopy: { flex: 1, gap: 2 },
  sourceHomeTitle: { color: '#241C16', fontSize: 14, fontWeight: '900' },
  sourceHomeDetail: { color: '#6D6257', fontSize: 12, lineHeight: 17 },
  sourceHomeState: { color: '#2F7448', fontSize: 12, fontWeight: '900' },
  providerSteps: { gap: 8 },
  providerStep: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  providerStepNumber: { width: 24, height: 24, borderRadius: 999, backgroundColor: '#241C16', color: '#FFFFFF', textAlign: 'center', lineHeight: 24, fontSize: 12, fontWeight: '900', overflow: 'hidden' },
  providerStepTitle: { color: '#241C16', fontSize: 13, fontWeight: '900' },
  providerActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  providerAction: { flexGrow: 1, flexBasis: '30%', borderRadius: 16, backgroundColor: '#EFE6ED', paddingHorizontal: 11, paddingVertical: 10, gap: 3 },
  providerActionTitle: { color: '#3F2D42', fontSize: 12, fontWeight: '900' },
  providerActionDetail: { color: '#6D6257', fontSize: 11, lineHeight: 15 },
  providerCta: { alignSelf: 'flex-start', backgroundColor: '#2F7448', borderRadius: 999, color: '#FFFFFF', fontSize: 13, fontWeight: '900', paddingHorizontal: 14, paddingVertical: 9 },
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
  feedItem: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, paddingVertical: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#E3DACB' },
  feedDot: { width: 24, height: 24, borderRadius: 8, backgroundColor: '#E4F1E8', marginTop: 2 },
  feedCopy: { flex: 1, gap: 3 },
  feedTitle: { color: '#241C16', fontSize: 15, fontWeight: '900' },
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
  formField: { borderRadius: 14, backgroundColor: '#F6F1E8', padding: 12, gap: 7 },
  formLabel: { color: '#241C16', fontWeight: '900', fontSize: 14 },
  formHint: { color: '#6D6257', fontSize: 12 },
  formInput: { minHeight: 42, borderRadius: 12, backgroundColor: '#FFFFFF', color: '#241C16', paddingHorizontal: 11, paddingVertical: 9, fontSize: 14 },
  formInputMultiline: { minHeight: 82, textAlignVertical: 'top' },
  checkRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, borderRadius: 14, backgroundColor: '#F6F1E8', padding: 12 },
  checkBox: { width: 24, height: 24, borderRadius: 8, borderWidth: 1, borderColor: '#B8AB9A', textAlign: 'center', color: '#FFFFFF', fontWeight: '900', overflow: 'hidden' },
  checkBoxOn: { backgroundColor: '#2F7448', borderColor: '#2F7448' },
  checkCopy: { flex: 1, gap: 2 },
  checkTitle: { color: '#241C16', fontWeight: '900', fontSize: 14 },
  checkDetail: { color: '#6D6257', fontSize: 12, lineHeight: 17 },
  calendarRow: { flexDirection: 'row', gap: 10, alignItems: 'flex-start', paddingVertical: 8, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#D8CFC2' },
  calendarDate: { minWidth: 68, color: '#2F7448', fontSize: 12, fontWeight: '900' },
  calendarCopy: { flex: 1, gap: 2 },
  timelineRow: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  timelineDot: { width: 12, height: 12, borderRadius: 999, backgroundColor: '#F3B15E', marginTop: 5 },
  timelineCopy: { flex: 1, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#D8CFC2', paddingBottom: 10 },
  galleryGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  galleryTile: { width: '47%', minHeight: 86, borderRadius: 18, backgroundColor: '#F6F1E8', padding: 12, justifyContent: 'space-between' },
  galleryGlyph: { color: '#2F7448', fontSize: 24, fontWeight: '900' },
  galleryText: { color: '#241C16', fontSize: 13, fontWeight: '900' },
  table: { minWidth: 420, borderRadius: 16, overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth, borderColor: '#D8CFC2' },
  tableRow: { flexDirection: 'row' },
  tableHeader: { width: 104, padding: 10, backgroundColor: '#E4F1E8', color: '#2F7448', fontSize: 12, fontWeight: '900' },
  tableCell: { width: 104, padding: 10, backgroundColor: '#FFFFFF', color: '#4E463E', fontSize: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#D8CFC2' },
  permissionRow: { borderRadius: 16, backgroundColor: '#F6F1E8', padding: 12, gap: 4 },
  permissionHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  permissionTitle: { color: '#241C16', fontWeight: '900' },
  permissionMeta: { color: '#2F7448', fontSize: 11, fontWeight: '900', textTransform: 'uppercase' },
  permissionDetail: { color: '#6D6257', fontSize: 13, lineHeight: 18 },
  swatches: { flexDirection: 'row', gap: 8 },
  swatch: { width: 42, height: 42, borderRadius: 14 },
});
