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
import { colors, radius } from '@/src/theme';

type Citation = { label: string; detail: string; href: string; tone: 'moss' | 'blue' | 'amber' };
type Answer = { title: string; intro: string; rows: Array<{ meal: string; use: string; next: string }>; citations: Citation[] };
type Message = { id: string; role: 'assistant' | 'user'; text: string; answer?: Answer };
type Thread = { id: string; title: string; detail: string; messages: Message[] };

const citations: Citation[] = [
  { label: 'Notion', detail: 'LifeOS 2026', href: 'https://app.notion.com/p/manasa-srinivas/LifeOS-2026-3a45dd535a93816fb7d3d4a0a2bc2bf1', tone: 'moss' },
  { label: 'Sheets', detail: 'LifeOS workbook', href: 'https://docs.google.com/spreadsheets/d/1WpEwm07ApcnuiLDVhzl8vy4D5kU8KjmtbAVC4qLphcU/edit', tone: 'blue' },
  { label: 'Web', detail: 'Recipe source', href: 'https://www.themediterraneandish.com', tone: 'amber' },
];

const dinnerAnswer: Answer = {
  title: 'A low-friction dinner plan',
  intro: 'Your kitchen already covers the main meal. One small grocery stop makes the rest of the week easier.',
  rows: [
    { meal: 'Tonight · Tandoori chicken', use: 'Chicken, yogurt, rice', next: 'Start the marinade at 6:45 PM' },
    { meal: 'Tomorrow · Green dal', use: 'Leftover dal, spinach', next: 'Add naan if you want a side' },
    { meal: 'Thursday · Yogurt bowl', use: 'Greek yogurt, berries', next: 'Use yogurt before Friday' },
  ],
  citations,
};

const initialThreads: Thread[] = [
  {
    id: 'dinner', title: 'Tonight’s dinner', detail: 'Kitchen-aware plan', messages: [
      { id: 'a1', role: 'assistant', text: 'I can see your Food workspace: three planned meals, two use-soon items, and the current grocery run. What would make tonight easier?' },
      { id: 'u1', role: 'user', text: 'Plan dinner around what I already have, and keep the rest of the week simple.' },
      { id: 'a2', role: 'assistant', text: 'Here is the shortest path through the week, grounded in your kitchen, groceries, and recipe notes.', answer: dinnerAnswer },
    ],
  },
  {
    id: 'kitchen', title: 'Kitchen questions', detail: 'Use-soon & inventory', messages: [
      { id: 'a3', role: 'assistant', text: 'Greek yogurt is the item to use first; it expires Friday and connects to tonight’s marinade plus breakfast.' },
    ],
  },
  {
    id: 'capture', title: 'Recipe capture', detail: 'Saved from the web', messages: [
      { id: 'a4', role: 'assistant', text: 'I saved the recipe source and mapped its ingredients to your Food workspace. Ask me to scale it, substitute, or add its missing items.' },
    ],
  },
];

function makeAnswer(input: string): Answer {
  const lower = input.toLowerCase();
  const focus = lower.includes('shop') || lower.includes('buy')
    ? 'A focused grocery pass'
    : lower.includes('yogurt') || lower.includes('expire')
      ? 'Use the yogurt first'
      : 'A sensible next step';
  return {
    title: focus,
    intro: 'I used the active Food context to keep this practical: your kitchen inventory, planned meals, and linked sources all point to the same next actions.',
    rows: lower.includes('shop') || lower.includes('buy')
      ? [
        { meal: 'Produce', use: 'Coriander, lemons', next: 'Supports tonight and green dal' },
        { meal: 'Dairy', use: 'Greek yogurt', next: 'Skip — 3 cups are already home' },
        { meal: 'Pantry', use: 'Naan', next: 'Optional side for two dinners' },
      ]
      : [
        { meal: 'Tonight', use: 'Tandoori chicken', next: 'Use yogurt in the marinade' },
        { meal: 'Tomorrow', use: 'Green dal', next: 'Pair with remaining spinach' },
        { meal: 'Breakfast', use: 'Yogurt bowl', next: 'Finish before Friday' },
      ],
    citations,
  };
}

export default function ChatScreen() {
  const { width } = useWindowDimensions();
  const isWide = width >= 820;
  const router = useRouter();
  const scrollRef = useRef<ScrollView>(null);
  const [threads, setThreads] = useState<Thread[]>(initialThreads);
  const [activeThreadId, setActiveThreadId] = useState(initialThreads[0].id);
  const [draft, setDraft] = useState('');
  const activeThread = useMemo(() => threads.find((thread) => thread.id === activeThreadId) ?? threads[0], [activeThreadId, threads]);

  useEffect(() => {
    const timer = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
    return () => clearTimeout(timer);
  }, [activeThreadId, activeThread?.messages.length]);

  const createThread = () => {
    const id = `thread-${Date.now()}`;
    const thread: Thread = {
      id,
      title: 'New conversation',
      detail: 'Food context on',
      messages: [{ id: `${id}-welcome`, role: 'assistant', text: 'Food context is on. I can help with your kitchen, recipes, meals, shopping, and the source records behind them.' }],
    };
    setThreads((current) => [thread, ...current]);
    setActiveThreadId(id);
    setDraft('');
  };

  const sendMessage = () => {
    const text = draft.trim();
    if (!text) return;
    const now = Date.now();
    const user: Message = { id: `u-${now}`, role: 'user', text };
    const assistant: Message = {
      id: `a-${now}`,
      role: 'assistant',
      text: 'I found the useful connections and kept the answer tied to the records you already maintain.',
      answer: makeAnswer(text),
    };
    setThreads((current) => current.map((thread) => thread.id === activeThreadId ? { ...thread, messages: [...thread.messages, user, assistant] } : thread));
    setDraft('');
  };

  return (
    <Page>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.fill}>
        <ScrollView ref={scrollRef} contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={sharedStyles.content}>
            <View style={styles.topbar}>
              <View><Text style={styles.brand}>LIFEOS / CHAT</Text><Text style={styles.date}>Food context · local demo</Text></View>
              <Pressable accessibilityRole="button" onPress={createThread} style={({ pressed }) => [styles.newThread, pressed && styles.pressed]}>
                <Text style={styles.newThreadText}>＋ New thread</Text>
              </Pressable>
            </View>
            <PageHeader eyebrow="Connected conversation" title="Talk to your life, not a blank prompt." subtitle="Hearth reasons over your connected Food records and keeps the sources close to the answer." />

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
                {isWide ? <View style={styles.threadFoot}><Pill tone="moss">Food context on</Pill><Text style={styles.threadFootText}>Chat cites the records it uses.</Text></View> : null}
              </Card>

              <Card style={styles.chatPanel}>
                <View style={styles.chatHeader}>
                  <View style={styles.assistantMark}><Text style={styles.assistantMarkText}>✦</Text></View>
                  <View style={styles.chatHeaderCopy}><Text style={styles.chatTitle}>{activeThread.title}</Text><Text style={styles.chatDetail}>Hearth · Food workspace available</Text></View>
                  <Pill tone="moss">Grounded</Pill>
                </View>

                <View style={styles.divider} />
                <View style={styles.messages}>
                  {activeThread.messages.map((message) => <MessageBubble key={message.id} message={message} />)}
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
                    <Pressable accessibilityRole="button" disabled={!draft.trim()} onPress={sendMessage} style={({ pressed }) => [styles.send, !draft.trim() && styles.sendDisabled, pressed && styles.pressed]}>
                      <Text style={styles.sendText}>Send ↑</Text>
                    </Pressable>
                  </View>
                  <View style={styles.composerFoot}><Text style={styles.composerHint}>Uses Kitchen, Meals, Recipes, Shopping, and their sources.</Text><Text style={styles.shortcut}>⌘ ↵</Text></View>
                </View>
              </Card>
            </View>

            <Card tone="blue" style={styles.contextCard}>
              <View style={styles.contextIcon}><Text>⌁</Text></View>
              <View style={styles.contextCopy}><Text style={styles.contextTitle}>What Hearth can see</Text><Text style={sharedStyles.muted}>3 kitchen items · 3 meals · 8 shopping items · 3 linked sources. Chat uses this connected context when it is relevant, then names the records it relied on.</Text></View>
              <ActionButton label="Open Food" quiet onPress={() => router.push('/food')} />
            </Card>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Page>
  );
}

function MessageBubble({ message }: { message: Message }) {
  const assistant = message.role === 'assistant';
  return (
    <View style={[styles.messageRow, !assistant && styles.userRow]}>
      {assistant ? <View style={styles.smallMark}><Text style={styles.smallMarkText}>✦</Text></View> : null}
      <View style={[styles.messageBlock, !assistant && styles.userMessageBlock]}>
        <Text style={styles.messageByline}>{assistant ? 'Hearth' : 'You'}</Text>
        <View style={[styles.bubble, !assistant && styles.userBubble]}><Text style={[styles.bubbleText, !assistant && styles.userBubbleText]}>{message.text}</Text></View>
        {message.answer ? <StructuredAnswer answer={message.answer} /> : null}
      </View>
    </View>
  );
}

function StructuredAnswer({ answer }: { answer: Answer }) {
  return (
    <View style={styles.answer}>
      <Text style={styles.answerTitle}>{answer.title}</Text>
      <Text style={styles.answerIntro}>{answer.intro}</Text>
      <View style={styles.table} accessibilityLabel="Structured meal plan">
        <View style={[styles.tableRow, styles.tableHeader]}><Text style={[styles.tableCell, styles.tableMeal, styles.tableHeaderText]}>WHEN</Text><Text style={[styles.tableCell, styles.tableUse, styles.tableHeaderText]}>USE</Text><Text style={[styles.tableCell, styles.tableNext, styles.tableHeaderText]}>NEXT</Text></View>
        {answer.rows.map((row) => <View key={row.meal} style={styles.tableRow}><Text style={[styles.tableCell, styles.tableMeal]}>{row.meal}</Text><Text style={[styles.tableCell, styles.tableUse]}>{row.use}</Text><Text style={[styles.tableCell, styles.tableNext]}>{row.next}</Text></View>)}
      </View>
      <Text style={styles.sourceLabel}>Sources used</Text>
      <View style={styles.citations}>{answer.citations.map((citation) => <CitationChip key={citation.label} citation={citation} />)}</View>
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
  sourceLabel: { color: colors.muted, fontSize: 10, fontWeight: '900', letterSpacing: 0.7, textTransform: 'uppercase', marginTop: 13, marginBottom: 7 },
  citations: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
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
  contextCard: { marginTop: 14, flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 12 },
  contextIcon: { width: 38, height: 38, borderRadius: 13, backgroundColor: '#C7D8DF', alignItems: 'center', justifyContent: 'center' },
  contextCopy: { flex: 1, minWidth: 180 },
  contextTitle: { color: colors.ink, fontSize: 14, fontWeight: '800', marginBottom: 4 },
});
