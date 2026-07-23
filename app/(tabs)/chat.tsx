import { useEffect, useMemo, useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import { Link, useRouter } from 'expo-router';

import { ActionButton, Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { ChatMessage, ChatRole, ChatThread } from '@/src/chat/types';
import { listChatThreads, makeWelcomeAnswer, resolveChatServerConfig, sendChatMessage, undoServerAction } from '@/src/chat/client';
import { Citation } from '@/src/chat/citations';
import { ensureCitations } from '@/src/chat/citations';
import { useLifeOSDatabase } from '@/src/db/provider';
import { colors, radius, useLifeOSTheme } from '@/src/theme';
import { loadCatalog } from '@/src/domain/catalog';
import { loadLifeOSSettings, usableAiProfiles, useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { listRecordsForDomain } from '@/src/db/records';
import type { CanonicalRecord } from '@/src/domain/runtime';

type MessageSourceMode = 'server' | 'direct' | 'offline';

type MessageRow = ChatMessage;
type SourceCardRow = NonNullable<NonNullable<MessageRow['answer']>['sourceCards']>[number];

const seedThreads: ChatThread[] = [
  {
    id: 'seed',
    title: 'Food context',
    detail: 'Empty conversation',
    messages: [{ id: 'seed-a1', role: 'assistant', text: 'Ask a question to start a conversation.' }],
  },
];

const seedModeNotice = {
  title: 'Local source mode',
  detail: 'No model key yet. Hearth still answers from local LifeOS records and shows citations.',
};

const promptBank = [
  'What can I cook tonight from what I already have?',
  'Show a table of available vs missing ingredients.',
  'What should I buy for green dal and tandoori chicken?',
  'Summarize nutrition and previous cooking notes.',
];

function countSetting(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const CHAT_SECTIONS = ['threads', 'sources', 'messages', 'promptRail', 'context'] as const;
type ChatSection = typeof CHAT_SECTIONS[number];
const WORKSPACE_CHAT_SECTIONS = new Set<ChatSection>(['threads', 'sources', 'messages', 'promptRail']);

function orderedChatSections(value: string) {
  const allowed = new Set<string>(CHAT_SECTIONS);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is ChatSection => allowed.has(section));
  const missing = CHAT_SECTIONS.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

export default function ChatScreen() {
  const { width } = useWindowDimensions();
  const isWide = width >= 820;
  const router = useRouter();
  const db = useLifeOSDatabase();
  const settings = useLifeOSSettingsSnapshot();
  const theme = useLifeOSTheme();
  const { activeDomainId, activeManifest } = loadCatalog();
  const domainLabel = activeManifest.label;
  const scrollRef = useRef<ScrollView>(null);

  const [threads, setThreads] = useState<ChatThread[]>(seedThreads);
  const [activeThreadId, setActiveThreadId] = useState(seedThreads[0].id);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [mode, setMode] = useState<MessageSourceMode>('offline');
  const [warnings, setWarnings] = useState<string[]>([]);
  const [activeRunId, setActiveRunId] = useState<string | null>(null);
  const [undoingActionId, setUndoingActionId] = useState<string | null>(null);
  const [sourceRecords, setSourceRecords] = useState<CanonicalRecord[]>([]);

  useEffect(() => {
    let cancelled = false;

    const bootstrap = async () => {
      const dbThreads = await listChatThreads(db);
      if (cancelled) return;

      const settings = await loadLifeOSSettings();
      const directReady = usableAiProfiles(settings).length > 0;
      const nextMode: MessageSourceMode = directReady ? 'direct' : 'offline';
      const localRecords = db ? await listRecordsForDomain(db, activeDomainId).catch(() => [] as CanonicalRecord[]) : [];
      if (!cancelled) {
        setSourceRecords(localRecords);
      }

      if (dbThreads.length) {
        setThreads(dbThreads);
        setActiveThreadId(dbThreads[0].id);
        setMode(nextMode);
      } else {
        const welcomeAnswer = makeWelcomeAnswer(localRecords, domainLabel);
        const baseThread: ChatThread = {
          id: 'thread-empty',
          title: `${domainLabel} context`,
          detail: nextMode === 'direct' ? 'Direct model ready' : seedModeNotice.detail,
          messages: [{
            id: 'seed-a1',
            role: 'assistant',
            text: welcomeAnswer.intro,
            answer: welcomeAnswer,
          }],
        };
        setThreads([baseThread]);
        setActiveThreadId(baseThread.id);
        setMode(nextMode);
      }
    };

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [activeDomainId, db, domainLabel]);

  const activeThread = useMemo(() => threads.find((thread) => thread.id === activeThreadId) ?? threads[0], [activeThreadId, threads]);
  const chatConfig = settings.runtime.surfaceConfig.chat;
  const sourceLimit = countSetting(chatConfig.sourceLimit, 8);
  const chatSections = orderedChatSections(chatConfig.sectionOrder);
  const workspaceSections = chatSections.filter((section) => WORKSPACE_CHAT_SECTIONS.has(section));

  useEffect(() => {
    if (!activeThread?.messages.some((message) => message.role === 'user')) {
      return undefined;
    }
    const timer = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
    return () => clearTimeout(timer);
  }, [activeThread?.messages.length]);

  const createThread = () => {
    const id = `thread-${Date.now()}`;
    const thread: ChatThread = {
      id,
      title: 'New conversation',
      detail: mode === 'direct' ? 'Direct model' : 'Local only',
      messages: [{ id: `${id}-welcome`, role: 'assistant', text: `${domainLabel} context is on. I can help with records, sources, and next actions.` }],
    };

    setThreads((current) => [thread, ...current]);
    setActiveThreadId(id);
    setDraft('');
  };

  const appendOrReplaceThread = (nextThread: ChatThread) => {
    setThreads((current) => {
      const exists = current.findIndex((thread) => thread.id === nextThread.id);
      if (exists >= 0) {
        const copy = [...current];
        copy[exists] = nextThread;
        return copy;
      }
      return [nextThread, ...current];
    });
  };

  const appendStreamingToken = (threadId: string, messageId: string, token: string) => {
    setThreads((current) => {
      const target = current.findIndex((thread) => thread.id === threadId);
      if (target < 0) {
        return current;
      }
      const copy = [...current];
      const thread = copy[target];
      const messageIndex = thread.messages.findIndex((message) => message.id === messageId);
      if (messageIndex < 0) {
        const nextMessages = [...thread.messages, {
          id: messageId,
          role: 'assistant' as ChatRole,
          text: token,
          answer: { title: 'LifeOS model response', intro: '', rows: [], citations: [] },
        }];
        copy[target] = { ...thread, messages: nextMessages };
        return copy;
      }
      const nextMessages = thread.messages.map((message, index) =>
        index === messageIndex
          ? {
              ...message,
              text: `${message.text}${token}`,
              answer: message.answer
                ? {
                    ...message.answer,
                    intro: message.answer.intro ?? '',
                  }
                : {
                    title: 'LifeOS model response',
                    intro: token,
                    rows: [],
                    citations: [],
                  },
            }
          : message,
      );
      copy[target] = { ...thread, messages: nextMessages };
      return copy;
    });
  };

  const sendMessage = async () => {
    const text = draft.trim();
    if (!text || sending) {
      return;
    }

    setSending(true);
    setActiveRunId(null);
    setWarnings([]);
    const streamRunId = `stream-${Date.now()}`;
    const userMessageId = `user-${streamRunId}`;
    const streamMessageId = `asst-${streamRunId}`;

    const userMessage: ChatMessage = {
      id: userMessageId,
      role: 'user' as ChatRole,
      text,
    };

    const placeholder: ChatMessage = {
      id: streamMessageId,
      role: 'assistant' as ChatRole,
      text: 'Reading your Food graph…',
      answer: { title: 'Working from sources', intro: 'Reading your Food graph…', rows: [], citations: [] },
    };

    const withPlaceholder: ChatThread = {
      ...activeThread,
      messages: [...activeThread.messages, userMessage, placeholder],
    };
    appendOrReplaceThread(withPlaceholder);

    const result = await sendChatMessage({
      db,
      text,
      conversationId: activeThread.id,
      domainId: activeDomainId,
      onModelToken: (token) => {
        appendStreamingToken(activeThread.id, streamMessageId, token);
      },
    });

    appendOrReplaceThread(result.thread);
    setActiveThreadId(result.conversationId);
    setMode(result.mode);
    setActiveRunId(result.serverRunId ?? null);
    if (result.serverError) {
      setWarnings([result.serverError, ...(result.warnings ?? [])]);
    } else if (result.warnings?.length) {
      setWarnings(result.warnings);
    } else if (!result.retryable) {
      setWarnings([]);
    }
    setDraft('');
    setSending(false);
  };

  const retryLatest = async () => {
    if (sending) {
      return;
    }
    const lastUser = [...activeThread.messages].reverse().find((message) => message.role === 'user');
    if (!lastUser) {
      return;
    }
    setSending(true);
    setWarnings([]);
    const result = await sendChatMessage({
      db,
      text: lastUser.text,
      conversationId: activeThread.id,
      domainId: activeDomainId,
      retryOfMessageId: lastUser.id,
    });
    appendOrReplaceThread(result.thread);
    setActiveThreadId(result.conversationId);
    setMode(result.mode);
    setActiveRunId(result.serverRunId ?? null);
    setSending(false);
    if (result.warnings?.length) {
      setWarnings(result.warnings);
    }
  };

  const setMessageReceipt = (threadId: string, messageId: string, updater: (message: ChatMessage) => ChatMessage) => {
    setThreads((current) =>
      current.map((thread) =>
        thread.id !== threadId
          ? thread
          : {
              ...thread,
              messages: thread.messages.map((message) =>
                message.id === messageId ? updater(message) : message
              ),
            },
      ),
    );
  };

  const undoMessageAction = async (threadId: string, message: MessageRow) => {
    const receipt = message.actionReceipt;
    if (!receipt?.id) {
      setWarnings(['No reversible action receipt on this answer.']);
      return;
    }

    const { serverUrl, serverToken } = await resolveChatServerConfig();
    if (!serverUrl) {
      setWarnings(['Undo needs the same LifeOS server that created this receipt. No hosted bridge or webhook is required.']);
      return;
    }

    setUndoingActionId(receipt.id);
    const result = await undoServerAction({
      actionId: receipt.id,
      baseUrl: serverUrl,
      token: serverToken,
      actor: 'hearth',
      idempotencyKey: `app-undo-${receipt.id}`,
    });
    setUndoingActionId(null);

    if (!result?.undo_result?.success) {
      setWarnings([result?.undo_result?.message || 'Undo failed. The server did not confirm rollback.']);
      return;
    }

    setMessageReceipt(threadId, message.id, (current) => ({
      ...current,
      actionReceipt: current.actionReceipt
        ? { ...current.actionReceipt, status: 'undone', updated_at: new Date().toISOString() }
        : current.actionReceipt,
    }));
    setWarnings([result.undo_result.message || 'Undo applied. Changed records were rolled back.']);
  };

  const renderThreadRail = () => (
    chatConfig.showThreads ? (
      <Card key="threads" style={[styles.threadPanel, isWide ? styles.threadPanelWide : styles.threadPanelMobile]}>
        <View style={styles.threadHeading}><Text style={[styles.panelLabel, { color: theme.colors.muted }]}>Threads</Text><Pressable accessibilityRole="button" onPress={createThread} hitSlop={8}><Text style={[styles.plus, { color: theme.colors.moss }]}>＋</Text></Pressable></View>
        <ScrollView horizontal={!isWide} showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.threadList, !isWide && styles.threadListMobile]}>
          {threads.map((thread) => {
            const active = thread.id === activeThreadId;
            return <Pressable key={thread.id} accessibilityRole="button" onPress={() => setActiveThreadId(thread.id)} style={({ pressed }) => [styles.thread, active && styles.threadActive, active && { backgroundColor: theme.colors.mossSoft }, pressed && styles.pressed]}>
              <Text numberOfLines={1} style={[styles.threadTitle, { color: active ? theme.colors.moss : theme.colors.ink }]}>{thread.title}</Text>
              <Text numberOfLines={1} style={[styles.threadDetail, { color: active ? theme.colors.moss : theme.colors.muted }]}>{thread.detail}</Text>
            </Pressable>;
          })}
        </ScrollView>
        {isWide ? (
          <View style={styles.threadFoot}>
            <Pill tone="moss">{domainLabel} context on</Pill>
            <Text style={[styles.threadFootText, { color: theme.colors.muted }]}>{sourceRecords.length} local records available. Chat cites sources it reads.</Text>
            <Text style={[styles.threadFootText, { color: theme.colors.muted }]}>Open source cards and Undo reversible actions when a write receipt exists.</Text>
          </View>
        ) : null}
      </Card>
    ) : null
  );

  const renderChatPanelSection = (section: ChatSection) => {
    switch (section) {
      case 'sources':
        return chatConfig.showSources ? (
          <View key={section} style={[styles.sourceStrip, { backgroundColor: theme.colors.canvas }]}>
            <Text style={[styles.sourceStripTitle, { color: theme.colors.muted }]}>{sourceRecords.length} source records loaded</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.sourcePills}>
              {sourceRecords.slice(0, sourceLimit).map((record) => (
                <Pressable key={record.id} accessibilityRole="link" onPress={() => router.push(`/record/${record.id}`)} style={({ pressed }) => [styles.sourcePill, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
                  <Text style={[styles.sourcePillTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
                  <Text style={[styles.sourcePillMeta, { color: theme.colors.muted }]} numberOfLines={1}>{record.collection} · {record.source.provider}</Text>
                </Pressable>
              ))}
              {!sourceRecords.length ? <Text style={[styles.sourceEmpty, { color: theme.colors.muted }]}>Connect Notion, Sheets, or local records to ground answers.</Text> : null}
            </ScrollView>
          </View>
        ) : null;
      case 'messages':
        return (
          <View key={section} style={styles.messages}>
            {activeThread.messages.map((message: MessageRow) => (
              <MessageBubble key={message.id} message={message} undoing={undoingActionId === message.actionReceipt?.id} onUndo={() => void undoMessageAction(activeThread.id, message)} />
            ))}
          </View>
        );
      case 'promptRail':
        return chatConfig.promptRail ? (
          <View key={section} style={styles.promptRail}>
            {promptBank.map((prompt) => (
              <Pressable key={prompt} accessibilityRole="button" onPress={() => setDraft(prompt)} style={({ pressed }) => [styles.promptChip, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
                <Text style={[styles.promptText, { color: theme.colors.ink }]}>{prompt}</Text>
              </Pressable>
            ))}
          </View>
        ) : null;
      default:
        return null;
    }
  };

  const renderWorkspaceSection = (section: ChatSection) => {
    if (section === 'threads') {
      return renderThreadRail();
    }
    return null;
  };

  return (
    <Page>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.fill}>
        <ScrollView ref={scrollRef} contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            <View style={styles.topbar}>
              <View>
                <Text style={[styles.brand, { color: theme.colors.moss }]}>LIFEOS / CHAT</Text>
                <Text style={[styles.date, { color: theme.colors.muted }]}>{domainLabel} context · {mode === 'direct' ? 'direct model' : 'offline'}</Text>
              </View>
              <Pressable accessibilityRole="button" onPress={createThread} style={({ pressed }) => [styles.newThread, { backgroundColor: theme.colors.ink }, pressed && styles.pressed]}>
                <Text style={[styles.newThreadText, { color: theme.colors.paper }]}>＋ New thread</Text>
              </Pressable>
            </View>
            <PageHeader eyebrow="Connected conversation" title="Talk to your life, not a blank prompt." subtitle={`Hearth reasons over ${domainLabel} records and keeps source cards close to the answer.`} />
            <View style={styles.capabilityGrid}>
              <Card tone="moss" style={styles.capabilityCard}>
                <Text style={[styles.capabilityNumber, { color: theme.colors.ink }]}>{sourceRecords.length}</Text>
                <Text style={[styles.capabilityTitle, { color: theme.colors.ink }]}>Sources in context</Text>
                <Text style={[styles.capabilityBody, { color: theme.colors.muted }]}>Chat cites sources, opens records, and keeps answers tied to exact graph items.</Text>
              </Card>
              <Card tone={mode === 'direct' ? 'plum' : 'blue'} style={styles.capabilityCard}>
                <Text style={[styles.capabilityNumber, { color: theme.colors.ink }]}>{mode === 'direct' ? 'AI' : 'Local'}</Text>
                <Text style={[styles.capabilityTitle, { color: theme.colors.ink }]}>Model route</Text>
                <Text style={[styles.capabilityBody, { color: theme.colors.muted }]}>{mode === 'direct' ? 'Direct provider keys from Settings.' : 'No provider key yet; source briefing still works locally.'}</Text>
              </Card>
              <Card tone="amber" style={styles.capabilityCard}>
                <Text style={[styles.capabilityNumber, { color: theme.colors.ink }]}>MCP</Text>
                <Text style={[styles.capabilityTitle, { color: theme.colors.ink }]}>Same contract</Text>
                <Text style={[styles.capabilityBody, { color: theme.colors.muted }]}>App chat, skills and external clients share schemas, sources and reversible actions.</Text>
              </Card>
            </View>

            {warnings.length ? <Card style={[styles.modeNote, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>{warnings.map((item) => <Text key={item} style={[styles.modeText, { color: theme.colors.muted }]}>{item}</Text>)}</Card> : null}

            <View style={[styles.workspace, isWide && styles.workspaceWide]}>
              {workspaceSections.map(renderWorkspaceSection)}

              <Card style={styles.chatPanel}>
                <View style={styles.chatHeader}>
                  <View style={[styles.assistantMark, { backgroundColor: theme.colors.plumSoft }]}><Text style={[styles.assistantMarkText, { color: theme.colors.plum }]}>✦</Text></View>
                  <View style={styles.chatHeaderCopy}><Text style={[styles.chatTitle, { color: theme.colors.ink }]}>{activeThread.title}</Text><Text style={[styles.chatDetail, { color: theme.colors.muted }]}>Hearth · {domainLabel} workspace available</Text></View>
                  <Pressable accessibilityRole="button" onPress={() => router.push('/settings')}>
                    <Pill tone={mode === 'direct' ? 'plum' : 'blue'}>
                      {mode === 'direct' ? 'Direct' : 'Set up AI'}
                    </Pill>
                  </Pressable>
                </View>

                <View style={[styles.divider, { backgroundColor: theme.colors.line }]} />
                {workspaceSections.filter((section) => section !== 'threads').map(renderChatPanelSection)}

                <View style={[styles.composerWrap, { backgroundColor: theme.colors.paper, borderTopColor: theme.colors.line }]}>
                  <View style={[styles.composer, { borderColor: theme.colors.line }]}>
                    <TextInput
                      accessibilityLabel="Chat message"
                      value={draft}
                      onChangeText={setDraft}
                      placeholder={`Ask about ${domainLabel.toLowerCase()} records, sources, or next actions...`}
                      placeholderTextColor={theme.colors.muted}
                      multiline
                      style={[styles.input, { color: theme.colors.ink }]}
                      onSubmitEditing={sendMessage}
                      blurOnSubmit={false}
                    />
                    <Pressable accessibilityRole="button" disabled={!draft.trim() || sending} onPress={sendMessage} style={({ pressed }) => [styles.send, { backgroundColor: theme.colors.moss }, (!draft.trim() || sending) && styles.sendDisabled, pressed && styles.pressed]}>
                      <Text style={[styles.sendText, { color: theme.colors.paper }]}>{sending ? 'Working…' : 'Send ↑'}</Text>
                    </Pressable>
                    {!sending ? <Pressable accessibilityRole="button" onPress={retryLatest} style={({ pressed }) => [styles.send, { backgroundColor: theme.colors.moss }, pressed && styles.pressed]}><Text style={[styles.sendText, { color: theme.colors.paper }]}>Retry</Text></Pressable> : null}
                  </View>
                  <View style={styles.composerFoot}>
                    <Text style={[styles.composerHint, { color: theme.colors.muted }]}>Uses enabled domains, skill instructions, source records, and model keys stored in app settings.</Text>
                    <Text style={[styles.shortcut, { color: theme.colors.muted }]}>{activeRunId ? 'running' : settings.runtime.webSearch ? 'web on' : 'web off'}</Text>
                  </View>
                </View>
              </Card>
            </View>

            {chatConfig.showContextCard ? (
              <Card tone="blue" style={styles.contextCard}>
                <View style={[styles.contextIcon, { backgroundColor: theme.colors.blueSoft }]}><Text>⌁</Text></View>
                <View style={styles.contextCopy}><Text style={[styles.contextTitle, { color: theme.colors.ink }]}>What Hearth can see</Text><Text style={[sharedStyles.muted, { color: theme.colors.muted }]}>{activeThread.messages.length} messages loaded · Chat cites sources · Open source cards from citations · Undo appears on reversible write receipts.</Text></View>
                <ActionButton label={`Open ${domainLabel}`} quiet onPress={() => router.push('/food')} />
              </Card>
            ) : null}
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Page>
  );
}

function MessageBubble({ message, undoing, onUndo }: { message: MessageRow; undoing: boolean; onUndo: () => void }) {
  const theme = useLifeOSTheme();
  const assistant = message.role === 'assistant';
  const answerRows = message.answer;
  const citations = answerRows?.citations?.length ? ensureCitations(answerRows.citations) : [];
  const hasUndo = assistant && message.actionReceipt?.status === 'completed' && (message.actionReceipt.record_ids?.length ?? 0) > 0;
  const visibleText = answerRows?.intro || message.text;

  return (
    <View style={[styles.messageRow, !assistant && styles.userRow]}>
      {assistant ? <View style={[styles.smallMark, { backgroundColor: theme.colors.plumSoft }]}><Text style={[styles.smallMarkText, { color: theme.colors.plum }]}>✦</Text></View> : null}
      <View style={[styles.messageBlock, !assistant && styles.userMessageBlock]}>
        <Text style={[styles.messageByline, { color: theme.colors.muted }]}>{assistant ? 'Hearth' : 'You'}</Text>
        <View style={[styles.bubble, { backgroundColor: theme.colors.canvas }, !assistant && styles.userBubble, !assistant && { backgroundColor: theme.colors.ink }]}><Text style={[styles.bubbleText, { color: theme.colors.ink }, !assistant && styles.userBubbleText, !assistant && { color: theme.colors.paper }]}>{visibleText}</Text></View>
        {answerRows ? <StructuredAnswer answer={answerRows} /> : null}
        {assistant && message.actionReceipt ? <ActionReceiptCard receipt={message.actionReceipt} /> : null}
        {!assistant ? null : citations.length ? <View style={styles.citationRow}>{citations.map((citation) => <CitationChip key={`${citation.label}-${citation.href}`} citation={citation} />)}</View> : null}
        {!assistant || !hasUndo ? null : <Pressable accessibilityRole="button" disabled={undoing} onPress={onUndo} style={({ pressed }) => [styles.undoButton, { backgroundColor: theme.colors.paper, borderColor: theme.colors.moss }, undoing && styles.sendDisabled, pressed && styles.pressed]}><Text style={[styles.undoButtonText, { color: theme.colors.ink }]}>{undoing ? 'Undoing…' : 'Undo'}</Text></Pressable>}
      </View>
    </View>
  );
}

function ActionReceiptCard({ receipt }: { receipt: NonNullable<MessageRow['actionReceipt']> }) {
  const theme = useLifeOSTheme();
  const recordCount = receipt.record_ids?.length ?? 0;
  const sourceCount = receipt.source_ids?.length ?? receipt.source_citations?.length ?? 0;
  const idLabel = receipt.id.length > 18 ? `${receipt.id.slice(0, 18)}…` : receipt.id;
  const statusTone = receipt.status === 'completed' ? 'moss' : receipt.status === 'undone' ? 'blue' : receipt.status === 'failed' ? 'amber' : 'plum';

  return (
    <View style={[styles.receiptCard, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
      <View style={styles.receiptTop}>
        <Text style={[styles.receiptTitle, { color: theme.colors.ink }]}>Action receipt</Text>
        <Pill tone={statusTone}>{receipt.status}</Pill>
      </View>
      <Text style={[styles.receiptMeta, { color: theme.colors.muted }]}>
        {(receipt.tool || 'LifeOS action')} · {(receipt.domain || 'domain')} · {recordCount} record{recordCount === 1 ? '' : 's'} changed · {sourceCount} source{sourceCount === 1 ? '' : 's'}
      </Text>
      <Text style={[styles.receiptId, { color: theme.colors.moss }]}>id {idLabel} · risk {receipt.risk || 'bounded'}</Text>
      {receipt.undo_deadline_at ? <Text style={[styles.receiptMeta, { color: theme.colors.muted }]}>Undo window until {receipt.undo_deadline_at}</Text> : null}
    </View>
  );
}

function StructuredAnswer({ answer }: { answer: NonNullable<MessageRow['answer']> }) {
  const theme = useLifeOSTheme();
  return (
    <View style={[styles.answer, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }]}>
      <Text style={[styles.answerTitle, { color: theme.colors.ink }]}>{answer.title}</Text>
      {answer.rows.length ? (
        <View style={[styles.table, { borderColor: theme.colors.line }]} accessibilityLabel="Structured answer table">
          <View style={[styles.tableRow, styles.tableHeader, { backgroundColor: theme.colors.canvas, borderTopColor: theme.colors.line }]}>
            {(answer.columns?.length ? answer.columns : ['When', 'Use', 'Next']).map((column) => (
              <Text key={column} style={[styles.tableCell, styles.tableHeaderText, { color: theme.colors.muted }]}>{column.toUpperCase()}</Text>
            ))}
          </View>
          {answer.rows.map((row, index) => {
            const cells = row.cells ?? [row.meal ?? '', row.use ?? '', row.next ?? ''];
            return (
              <View key={`${index}-${cells.join('|')}`} style={[styles.tableRow, { backgroundColor: theme.colors.paper, borderTopColor: theme.colors.line }]}>
                {cells.map((cell, cellIndex) => <Text key={`${cellIndex}-${cell}`} style={[styles.tableCell, { color: theme.colors.ink }]}>{cell}</Text>)}
              </View>
            );
          })}
        </View>
      ) : null}
      {answer.recordCards?.length ? (
        <View style={styles.recordCards}>
          {answer.recordCards.map((record) => (
            <Link key={record.id} href={{ pathname: '/record/[id]', params: { id: record.id } }} asChild>
              <Pressable accessibilityRole="link" style={({ pressed }) => [styles.answerRecord, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }, pressed && styles.pressed]}>
                <View style={styles.answerRecordTop}>
                  <Text style={[styles.answerRecordTitle, { color: theme.colors.ink }]} numberOfLines={1}>{record.title}</Text>
                  <Pill tone="moss">{record.status}</Pill>
                </View>
                <Text style={[styles.answerRecordMeta, { color: theme.colors.muted }]} numberOfLines={1}>{record.collection} · {record.detail}</Text>
                {record.bullets?.slice(0, 3).map((bullet) => (
                  <Text key={bullet} style={[styles.answerRecordBullet, { color: theme.colors.ink }]} numberOfLines={2}>• {bullet}</Text>
                ))}
                <Text style={[styles.answerRecordSource, { color: theme.colors.moss }]} numberOfLines={1}>{record.source}</Text>
              </Pressable>
            </Link>
          ))}
        </View>
      ) : null}
      {answer.sourceCards?.length ? (
        <View style={styles.sourceEvidence}>
          <Text style={[styles.sourceEvidenceTitle, { color: theme.colors.ink }]}>Source evidence</Text>
          {answer.sourceCards.map((source) => (
            <CitationEvidenceCard key={`${source.id}-${source.href}`} source={source} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

function CitationEvidenceCard({ source }: { source: SourceCardRow }) {
  const router = useRouter();
  const theme = useLifeOSTheme();
  const open = () => {
    const recordPath = source.href.startsWith('wonderfood://record/')
      ? source.href.replace('wonderfood://record/', '')
      : '';
    const recordId = recordPath.split('/').filter(Boolean).at(-1) ?? '';
    if (recordId) {
      router.push(`/record/${decodeURIComponent(recordId)}`);
      return;
    }
    void Linking.openURL(source.href);
  };
  return (
    <Pressable accessibilityRole="link" onPress={open} style={({ pressed }) => [styles.sourceEvidenceCard, { backgroundColor: theme.colors.canvas, borderColor: theme.colors.line }, pressed && styles.pressed]}>
      <View style={styles.sourceEvidenceTop}>
        <Text style={[styles.sourceEvidenceLabel, { color: theme.colors.ink }]} numberOfLines={1}>{source.label}</Text>
        <Pill tone={source.tone}>{source.fields?.length ? `${source.fields.length} fields` : 'source'}</Pill>
      </View>
      <Text style={[styles.sourceEvidenceDetail, { color: theme.colors.muted }]} numberOfLines={1}>{source.detail}</Text>
      <Text style={[styles.sourceEvidenceQuote, { color: theme.colors.ink }]} numberOfLines={4}>“{source.quote}”</Text>
    </Pressable>
  );
}

function CitationChip({ citation }: { citation: Citation }) {
  const router = useRouter();
  const theme = useLifeOSTheme();
  const openCitation = () => {
    const recordId = citation.href.startsWith('wonderfood://record/')
      ? citation.href.replace('wonderfood://record/', '')
      : '';
    if (recordId) {
      router.push(`/record/${recordId}`);
      return;
    }
    void Linking.openURL(citation.href);
  };

  return (
    <Pressable accessibilityRole="link" onPress={openCitation} style={({ pressed }) => [styles.citation, citationToneStyle(citation.tone), citationToneDynamicStyle(citation.tone, theme.colors), pressed && styles.pressed]}>
      <Text style={[styles.citationLabel, { color: theme.colors.ink }]}>{citation.label}</Text><Text numberOfLines={1} style={[styles.citationDetail, { color: theme.colors.muted }]}>{citation.detail} ↗</Text>
    </Pressable>
  );
}

function citationToneDynamicStyle(tone: Citation['tone'], themed: typeof colors) {
  if (tone === 'blue') return { backgroundColor: themed.blueSoft };
  if (tone === 'amber') return { backgroundColor: themed.amberSoft };
  if (tone === 'plum') return { backgroundColor: themed.plumSoft };
  if (tone === 'neutral') return { backgroundColor: themed.canvas };
  return { backgroundColor: themed.mossSoft };
}

function citationToneStyle(tone: Citation['tone']) {
  if (tone === 'blue') return styles.citationBlue;
  if (tone === 'amber') return styles.citationAmber;
  if (tone === 'plum') return styles.citationPlum;
  if (tone === 'neutral') return styles.citationNeutral;
  return styles.citationMoss;
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  scrollContent: { flexGrow: 1 },
  topbar: { paddingTop: 16, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  brand: { color: colors.moss, fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  date: { color: colors.muted, fontSize: 12, marginTop: 3 },
  newThread: { backgroundColor: colors.ink, borderRadius: radius.pill, paddingHorizontal: 14, paddingVertical: 9 },
  newThreadText: { color: '#FFF', fontSize: 12, fontWeight: '800' },
  pressed: { opacity: 0.64 },
  workspace: { gap: 12 },
  workspaceWide: { flexDirection: 'row', alignItems: 'stretch' },
  capabilityGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 12 },
  capabilityCard: { flexGrow: 1, flexBasis: 230, minHeight: 142 },
  capabilityNumber: { color: colors.ink, fontSize: 24, lineHeight: 28, fontWeight: '900', letterSpacing: -0.6 },
  capabilityTitle: { color: colors.ink, fontSize: 14, fontWeight: '900', marginTop: 9 },
  capabilityBody: { color: colors.muted, fontSize: 12, lineHeight: 17, marginTop: 5 },
  threadPanel: { padding: 10 },
  threadPanelWide: { width: 242, minHeight: 580 },
  threadPanelMobile: { paddingVertical: 9 },
  threadHeading: { paddingHorizontal: 6, paddingVertical: 6, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  panelLabel: { color: colors.muted, fontSize: 11, fontWeight: '900', letterSpacing: 0.9, textTransform: 'uppercase' },
  plus: { color: colors.moss, fontSize: 20, lineHeight: 21, fontWeight: '500' },
  threadList: { gap: 4 },
  threadListMobile: { paddingHorizontal: 2 },
  thread: { borderRadius: 12, paddingHorizontal: 10, paddingVertical: 10, minWidth: 160 },
  threadActive: { backgroundColor: colors.mossSoft },
  threadTitle: { color: colors.ink, fontSize: 13, fontWeight: '800' },
  threadTitleActive: { color: colors.moss },
  threadDetail: { color: colors.muted, fontSize: 11, marginTop: 3 },
  threadDetailActive: { color: colors.moss },
  threadFoot: { marginTop: 'auto', padding: 9, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, gap: 7 },
  threadFootText: { color: colors.muted, fontSize: 11, lineHeight: 15 },
  chatPanel: { flex: 1, minWidth: 0, padding: 0, overflow: 'hidden', minHeight: 580 },
  chatHeader: { padding: 15, flexDirection: 'row', alignItems: 'center', gap: 10 },
  assistantMark: { width: 36, height: 36, borderRadius: 12, backgroundColor: colors.plumSoft, alignItems: 'center', justifyContent: 'center' },
  assistantMarkText: { color: colors.plum, fontSize: 18, fontWeight: '800' },
  chatHeaderCopy: { flex: 1, minWidth: 0 },
  chatTitle: { color: colors.ink, fontSize: 15, fontWeight: '800' },
  chatDetail: { color: colors.muted, fontSize: 11, marginTop: 2 },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: colors.line },
  messages: { flex: 1, padding: 16, gap: 18 },
  messageRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  userRow: { justifyContent: 'flex-end' },
  smallMark: { width: 25, height: 25, borderRadius: 9, backgroundColor: colors.plumSoft, alignItems: 'center', justifyContent: 'center', marginTop: 18 },
  smallMarkText: { color: colors.plum, fontSize: 12, fontWeight: '900' },
  messageBlock: { maxWidth: 680, flexShrink: 1, minWidth: 0 },
  userMessageBlock: { alignItems: 'flex-end', maxWidth: '84%' },
  messageByline: { color: colors.muted, fontSize: 11, fontWeight: '800', marginBottom: 5 },
  bubble: { backgroundColor: '#F0F0E9', borderRadius: 15, borderTopLeftRadius: 4, paddingHorizontal: 12, paddingVertical: 10 },
  userBubble: { backgroundColor: colors.ink, borderTopLeftRadius: 15, borderTopRightRadius: 4 },
  bubbleText: { color: colors.ink, fontSize: 14, lineHeight: 21 },
  userBubbleText: { color: '#FFF' },
  answer: { minWidth: 0, marginTop: 9, borderWidth: 1, borderColor: '#CBD8D0', backgroundColor: '#F9FCF8', borderRadius: 14, padding: 12 },
  answerTitle: { color: colors.ink, fontSize: 14, fontWeight: '800' },
  table: { borderWidth: 1, borderColor: colors.line, borderRadius: 10, overflow: 'hidden' },
  tableRow: { minWidth: 0, flexDirection: 'row', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, backgroundColor: colors.paper },
  tableHeader: { borderTopWidth: 0, backgroundColor: '#ECEEE7' },
  tableCell: { minWidth: 0, flex: 1, flexShrink: 1, color: colors.ink, fontSize: 11, lineHeight: 15, padding: 8 },
  tableHeaderText: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 0.6 },
  recordCards: { marginTop: 10, gap: 8 },
  answerRecord: { borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, borderRadius: 12, padding: 10 },
  answerRecordTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  answerRecordTitle: { color: colors.ink, fontSize: 14, fontWeight: '900', flex: 1 },
  answerRecordMeta: { color: colors.muted, fontSize: 11, marginTop: 4 },
  answerRecordBullet: { color: colors.ink, fontSize: 12, lineHeight: 17, marginTop: 6 },
  answerRecordSource: { color: colors.moss, fontSize: 10, fontWeight: '800', marginTop: 8 },
  sourceEvidence: { marginTop: 10, gap: 8 },
  sourceEvidenceTitle: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  sourceEvidenceCard: { borderWidth: 1, borderColor: colors.line, backgroundColor: colors.canvas, borderRadius: 12, padding: 10 },
  sourceEvidenceTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  sourceEvidenceLabel: { color: colors.ink, fontSize: 13, fontWeight: '900', flex: 1 },
  sourceEvidenceDetail: { color: colors.muted, fontSize: 10, marginTop: 4 },
  sourceEvidenceQuote: { color: colors.ink, fontSize: 12, lineHeight: 17, marginTop: 7 },
  citationRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 9 },
  citation: { borderRadius: 9, paddingHorizontal: 8, paddingVertical: 6, maxWidth: 166 },
  citationMoss: { backgroundColor: colors.mossSoft },
  citationBlue: { backgroundColor: colors.blueSoft },
  citationAmber: { backgroundColor: colors.amberSoft },
  citationPlum: { backgroundColor: colors.plumSoft },
  citationNeutral: { backgroundColor: '#ECEBE3' },
  citationLabel: { color: colors.ink, fontSize: 10, fontWeight: '900' },
  citationDetail: { color: colors.muted, fontSize: 10, marginTop: 1 },
  receiptCard: { marginTop: 9, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, borderRadius: 12, padding: 10 },
  receiptTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  receiptTitle: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  receiptMeta: { color: colors.muted, fontSize: 11, lineHeight: 15, marginTop: 5 },
  receiptId: { color: colors.moss, fontSize: 10, fontWeight: '900', marginTop: 6 },
  composerWrap: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, padding: 12, backgroundColor: '#FEFEFA' },
  sourceStrip: { paddingHorizontal: 14, paddingVertical: 10, backgroundColor: '#FBFBF4' },
  sourceStripTitle: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 7 },
  sourcePills: { gap: 8, paddingRight: 8 },
  sourcePill: { width: 150, borderRadius: 12, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, paddingHorizontal: 10, paddingVertical: 8 },
  sourcePillTitle: { color: colors.ink, fontSize: 12, fontWeight: '900' },
  sourcePillMeta: { color: colors.muted, fontSize: 10, marginTop: 3 },
  sourceEmpty: { color: colors.muted, fontSize: 12, paddingVertical: 8 },
  promptRail: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, paddingHorizontal: 12, paddingVertical: 10 },
  promptChip: { minHeight: 42, maxWidth: 250, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, paddingHorizontal: 12, paddingVertical: 9, justifyContent: 'center' },
  promptRailMobile: { gap: 8, paddingHorizontal: 12, paddingVertical: 10 },
  promptChipMobile: { borderRadius: 13, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.paper, paddingHorizontal: 12, paddingVertical: 10 },
  promptText: { color: colors.ink, fontSize: 12, fontWeight: '800' },
  composer: { minHeight: 51, borderWidth: 1, borderColor: '#CBCBC0', borderRadius: 14, paddingLeft: 12, paddingRight: 7, paddingVertical: 6, flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  input: { minWidth: 0, color: colors.ink, fontSize: 14, lineHeight: 20, paddingTop: 7, paddingBottom: 7, flex: 1, minHeight: 34, maxHeight: 102 },
  send: { backgroundColor: colors.moss, borderRadius: 10, minHeight: 35, paddingHorizontal: 11, alignItems: 'center', justifyContent: 'center' },
  sendDisabled: { backgroundColor: '#A9ADA4' },
  sendText: { color: '#FFF', fontSize: 12, fontWeight: '800' },
  composerFoot: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginTop: 7 },
  composerHint: { color: colors.muted, fontSize: 10, lineHeight: 14, flex: 1 },
  shortcut: { color: colors.muted, fontSize: 10, fontWeight: '700' },
  undoButton: { marginTop: 8, alignSelf: 'flex-start', backgroundColor: '#FFF', borderWidth: 1, borderColor: colors.moss, borderRadius: 10, paddingHorizontal: 10, paddingVertical: 6 },
  undoButtonText: { color: colors.ink, fontSize: 11, fontWeight: '800' },
  contextCard: { marginTop: 14, flexDirection: 'row', flexWrap: 'wrap', gap: 12, alignItems: 'center' },
  contextIcon: { width: 38, height: 38, borderRadius: 13, backgroundColor: '#C7D8DF', alignItems: 'center', justifyContent: 'center' },
  contextCopy: { flex: 1, minWidth: 180 },
  contextTitle: { color: colors.ink, fontSize: 14, fontWeight: '800', marginBottom: 4 },
  modeNote: { padding: 12, borderColor: colors.line, borderWidth: 1, backgroundColor: '#F7F7F0' },
  modeText: { color: colors.muted, fontSize: 12, lineHeight: 17 },
});
