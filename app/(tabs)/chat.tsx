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
import { useRouter } from 'expo-router';

import { ActionButton, Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { ChatMessage, ChatRole, ChatThread } from '@/src/chat/types';
import { listChatThreads, sendChatMessage } from '@/src/chat/client';
import { Citation } from '@/src/chat/citations';
import { ensureCitations } from '@/src/chat/citations';
import { useLifeOSDatabase } from '@/src/db/provider';
import { colors, radius } from '@/src/theme';
import { loadCatalog } from '@/src/domain/catalog';
import { loadLifeOSSettings, usableAiProfiles } from '@/src/settings/lifeos-settings';

type MessageSourceMode = 'server' | 'direct' | 'offline';

type MessageRow = ChatMessage;

const seedThreads: ChatThread[] = [
  {
    id: 'seed',
    title: 'Food context',
    detail: 'Empty conversation',
    messages: [{ id: 'seed-a1', role: 'assistant', text: 'Ask a question to start a conversation.' }],
  },
];

const seedModeNotice = {
  title: 'Offline mode',
  detail: 'No server endpoint configured. Messages are local and not source-grounded.',
};

export default function ChatScreen() {
  const { width } = useWindowDimensions();
  const isWide = width >= 820;
  const router = useRouter();
  const db = useLifeOSDatabase();
  const { activeDomainId } = loadCatalog();
  const scrollRef = useRef<ScrollView>(null);

  const [threads, setThreads] = useState<ChatThread[]>(seedThreads);
  const [activeThreadId, setActiveThreadId] = useState(seedThreads[0].id);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [mode, setMode] = useState<MessageSourceMode>('offline');
  const [warnings, setWarnings] = useState<string[]>([]);
  const [activeRunId, setActiveRunId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const bootstrap = async () => {
      const dbThreads = await listChatThreads(db);
      if (cancelled) return;

      const settings = await loadLifeOSSettings();
      const directReady = usableAiProfiles(settings).length > 0;
      const nextMode: MessageSourceMode = directReady ? 'direct' : 'offline';

      if (dbThreads.length) {
        setThreads(dbThreads);
        setActiveThreadId(dbThreads[0].id);
        setMode(nextMode);
      } else {
        const baseThread: ChatThread = {
          id: 'thread-empty',
          title: 'Food context',
          detail: nextMode === 'direct' ? 'Direct model ready' : seedModeNotice.detail,
          messages: seedThreads[0].messages,
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
  }, [db]);

  const activeThread = useMemo(() => threads.find((thread) => thread.id === activeThreadId) ?? threads[0], [activeThreadId, threads]);

  useEffect(() => {
    const timer = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
    return () => clearTimeout(timer);
  }, [activeThread?.messages.length]);

  const createThread = () => {
    const id = `thread-${Date.now()}`;
    const thread: ChatThread = {
      id,
      title: 'New conversation',
      detail: mode === 'direct' ? 'Direct model' : 'Local only',
      messages: [{ id: `${id}-welcome`, role: 'assistant', text: 'Food context is on. I can help with meals, recipes, shopping, and source-backed actions.' }],
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
    const streamMessageId = `asst-${streamRunId}`;

    const placeholder: ChatMessage = {
      id: streamMessageId,
      role: 'assistant' as ChatRole,
      text: '',
      answer: { title: 'LifeOS model response', intro: '', rows: [], citations: [] },
    };

    const withPlaceholder: ChatThread = {
      ...activeThread,
      messages: [...activeThread.messages, placeholder],
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
    void threadId;
    void message;
    setWarnings(['This direct-provider answer has no reversible action.']);
  };

  return (
    <Page>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.fill}>
        <ScrollView ref={scrollRef} contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            <View style={styles.topbar}>
              <View>
                <Text style={styles.brand}>LIFEOS / CHAT</Text>
                <Text style={styles.date}>Food context · {mode === 'direct' ? 'direct model' : 'offline'}</Text>
              </View>
              <Pressable accessibilityRole="button" onPress={createThread} style={({ pressed }) => [styles.newThread, pressed && styles.pressed]}>
                <Text style={styles.newThreadText}>＋ New thread</Text>
              </Pressable>
            </View>
            <PageHeader eyebrow="Connected conversation" title="Talk to your life, not a blank prompt." subtitle="Hearth reasons over your Food records and keeps source cards close to the answer." />

            {warnings.length ? <Card style={styles.modeNote}>{warnings.map((item) => <Text key={item} style={styles.modeText}>{item}</Text>)}</Card> : null}

            <View style={[styles.workspace, isWide && styles.workspaceWide]}>
              <Card style={[styles.threadPanel, isWide ? styles.threadPanelWide : styles.threadPanelMobile]}>
                <View style={styles.threadHeading}><Text style={styles.panelLabel}>Threads</Text><Pressable accessibilityRole="button" onPress={createThread} hitSlop={8}><Text style={styles.plus}>＋</Text></Pressable></View>
                <ScrollView horizontal={!isWide} showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.threadList, !isWide && styles.threadListMobile]}>
                  {threads.map((thread) => {
                    const active = thread.id === activeThreadId;
                    return <Pressable key={thread.id} accessibilityRole="button" onPress={() => setActiveThreadId(thread.id)} style={({ pressed }) => [styles.thread, active && styles.threadActive, pressed && styles.pressed]}>
                      <Text numberOfLines={1} style={[styles.threadTitle, active && styles.threadTitleActive]}>{thread.title}</Text>
                      <Text numberOfLines={1} style={[styles.threadDetail, active && styles.threadDetailActive]}>{thread.detail}</Text>
                    </Pressable>;
                  })}
                </ScrollView>
                {isWide ? <View style={styles.threadFoot}><Pill tone="moss">Food context on</Pill><Text style={styles.threadFootText}>Chat cites sources it reads.</Text></View> : null}
              </Card>

              <Card style={styles.chatPanel}>
                <View style={styles.chatHeader}>
                  <View style={styles.assistantMark}><Text style={styles.assistantMarkText}>✦</Text></View>
                  <View style={styles.chatHeaderCopy}><Text style={styles.chatTitle}>{activeThread.title}</Text><Text style={styles.chatDetail}>Hearth · Food workspace available</Text></View>
                  <Pressable accessibilityRole="button" onPress={() => router.push('/settings')}>
                    <Pill tone={mode === 'direct' ? 'plum' : 'blue'}>
                      {mode === 'direct' ? 'Direct' : 'Set up AI'}
                    </Pill>
                  </Pressable>
                </View>

                <View style={styles.divider} />
                <View style={styles.messages}>
                  {activeThread.messages.map((message: MessageRow) => (
                    <MessageBubble key={message.id} message={message} onUndo={() => void undoMessageAction(activeThread.id, message)} />
                  ))}
                </View>

                <View style={styles.composerWrap}>
                  <View style={styles.composer}>
                    <TextInput
                      accessibilityLabel="Chat message"
                      value={draft}
                      onChangeText={setDraft}
                      placeholder="Ask about meals, the kitchen, or your grocery run…"
                      placeholderTextColor={colors.muted}
                      multiline
                      style={styles.input}
                      onSubmitEditing={sendMessage}
                      blurOnSubmit={false}
                    />
                    <Pressable accessibilityRole="button" disabled={!draft.trim() || sending} onPress={sendMessage} style={({ pressed }) => [styles.send, (!draft.trim() || sending) && styles.sendDisabled, pressed && styles.pressed]}>
                      <Text style={styles.sendText}>{sending ? 'Working…' : 'Send ↑'}</Text>
                    </Pressable>
                    {!sending ? <Pressable accessibilityRole="button" onPress={retryLatest} style={({ pressed }) => [styles.send, pressed && styles.pressed]}><Text style={styles.sendText}>Retry</Text></Pressable> : null}
                  </View>
                  <View style={styles.composerFoot}><Text style={styles.composerHint}>Uses Kitchen, Meals, Recipes, Shopping, and their sources.</Text><Text style={styles.shortcut}>⌘ ↵</Text></View>
                </View>
              </Card>
            </View>

            <Card tone="blue" style={styles.contextCard}>
              <View style={styles.contextIcon}><Text>⌁</Text></View>
              <View style={styles.contextCopy}><Text style={styles.contextTitle}>What Hearth can see</Text><Text style={sharedStyles.muted}>{activeThread.messages.length} messages loaded · open conversation resume in order · citations inline when available.</Text></View>
              <ActionButton label="Open Food" quiet onPress={() => router.push('/food')} />
            </Card>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Page>
  );
}

function MessageBubble({ message, onUndo }: { message: MessageRow; onUndo: () => void }) {
  const assistant = message.role === 'assistant';
  const answerRows = message.answer;
  const citations = answerRows?.citations?.length ? ensureCitations(answerRows.citations) : [];
  const hasUndo = assistant && message.actionReceipt?.status === 'completed' && (message.actionReceipt.record_ids?.length ?? 0) > 0;

  return (
    <View style={[styles.messageRow, !assistant && styles.userRow]}>
      {assistant ? <View style={styles.smallMark}><Text style={styles.smallMarkText}>✦</Text></View> : null}
      <View style={[styles.messageBlock, !assistant && styles.userMessageBlock]}>
        <Text style={styles.messageByline}>{assistant ? 'Hearth' : 'You'}</Text>
        <View style={[styles.bubble, !assistant && styles.userBubble]}><Text style={[styles.bubbleText, !assistant && styles.userBubbleText]}>{message.text}</Text></View>
        {answerRows ? <StructuredAnswer answer={answerRows} /> : null}
        {!assistant ? null : citations.length ? <View style={styles.citationRow}>{citations.map((citation) => <CitationChip key={`${citation.label}-${citation.href}`} citation={citation} />)}</View> : null}
        {!assistant || !hasUndo ? null : <Pressable accessibilityRole="button" onPress={onUndo} style={({ pressed }) => [styles.undoButton, pressed && styles.pressed]}><Text style={styles.undoButtonText}>Undo</Text></Pressable>}
      </View>
    </View>
  );
}

function StructuredAnswer({ answer }: { answer: NonNullable<MessageRow['answer']> }) {
  return (
    <View style={styles.answer}>
      <Text style={styles.answerTitle}>{answer.title}</Text>
      <Text style={styles.answerIntro}>{answer.intro}</Text>
      <View style={styles.table} accessibilityLabel="Structured meal plan">
        <View style={[styles.tableRow, styles.tableHeader]}><Text style={[styles.tableCell, styles.tableMeal, styles.tableHeaderText]}>WHEN</Text><Text style={[styles.tableCell, styles.tableUse, styles.tableHeaderText]}>USE</Text><Text style={[styles.tableCell, styles.tableNext, styles.tableHeaderText]}>NEXT</Text></View>
        {answer.rows.map((row) => <View key={`${row.meal}-${row.use}`} style={styles.tableRow}><Text style={[styles.tableCell, styles.tableMeal]}>{row.meal}</Text><Text style={[styles.tableCell, styles.tableUse]}>{row.use}</Text><Text style={[styles.tableCell, styles.tableNext]}>{row.next}</Text></View>)}
      </View>
    </View>
  );
}

function CitationChip({ citation }: { citation: Citation }) {
  return (
    <Pressable accessibilityRole="link" onPress={() => { void Linking.openURL(citation.href); }} style={({ pressed }) => [styles.citation, styles[`citation${citation.tone[0].toUpperCase()}${citation.tone.slice(1)}` as 'citationMoss'], pressed && styles.pressed]}>
      <Text style={styles.citationLabel}>{citation.label}</Text><Text numberOfLines={1} style={styles.citationDetail}>{citation.detail} ↗</Text>
    </Pressable>
  );
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
  answerIntro: { color: colors.muted, fontSize: 12, lineHeight: 18, marginTop: 4, marginBottom: 12 },
  table: { borderWidth: 1, borderColor: colors.line, borderRadius: 10, overflow: 'hidden' },
  tableRow: { minWidth: 0, flexDirection: 'row', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, backgroundColor: colors.paper },
  tableHeader: { borderTopWidth: 0, backgroundColor: '#ECEEE7' },
  tableCell: { minWidth: 0, flexShrink: 1, color: colors.ink, fontSize: 11, lineHeight: 15, padding: 8 },
  tableMeal: { flex: 1.1, fontWeight: '700' },
  tableUse: { flex: 1 },
  tableNext: { flex: 1.2 },
  tableHeaderText: { color: colors.muted, fontSize: 9, fontWeight: '900', letterSpacing: 0.6 },
  citationRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 9 },
  citation: { borderRadius: 9, paddingHorizontal: 8, paddingVertical: 6, maxWidth: 166 },
  citationMoss: { backgroundColor: colors.mossSoft },
  citationBlue: { backgroundColor: colors.blueSoft },
  citationAmber: { backgroundColor: colors.amberSoft },
  citationLabel: { color: colors.ink, fontSize: 10, fontWeight: '900' },
  citationDetail: { color: colors.muted, fontSize: 10, marginTop: 1 },
  composerWrap: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, padding: 12, backgroundColor: '#FEFEFA' },
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
