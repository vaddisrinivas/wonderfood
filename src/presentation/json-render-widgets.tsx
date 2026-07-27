import type { ComponentRegistry, ComponentRenderProps } from '@json-render/react-native';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { sendChatMessage } from '@/src/chat/client';
import type { ChatMessage, ChatThread } from '@/src/chat/types';
import { useLifeOSDatabase } from '@/src/db/provider';
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
  return (
    <WidgetShell
      title={text(props.title, 'AI package editor')}
      subtitle={text(props.subtitle, 'Describe table, screen, theme, or workflow changes. Wonder should produce a reviewable package diff.')}
    >
      <Text style={styles.bodyText}>Current production rule: no hidden edits. Package changes should go through proposal, validation, approval, then apply.</Text>
      <Text style={styles.bodyText}>Supported targets: collections, fields, views, screens, theme tokens, rules, provider settings, and widget choices.</Text>
    </WidgetShell>
  );
}

function WidgetCatalogWidget({ element }: ComponentRenderProps<WidgetProps>) {
  const props = element.props ?? {};
  const widgets = ['Assistant chat', 'Health Connect', 'Record lists', 'Metrics', 'Actions', 'Text cards', 'Package editor'];
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

export const JSON_RENDER_WIDGET_REGISTRY: ComponentRegistry = {
  AssistantChatWidget,
  HealthConnectWidget,
  SchemaEditorWidget,
  WidgetCatalogWidget,
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
});
