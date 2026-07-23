import { PropsWithChildren, ReactNode } from 'react';
import { Pressable, StyleProp, StyleSheet, Text, View, ViewStyle } from 'react-native';
import { Link } from 'expo-router';

import { colors, radius, shadow, useLifeOSTheme } from '@/src/theme';

export function Page({ children }: PropsWithChildren) {
  const theme = useLifeOSTheme();
  return <View style={[styles.page, theme.density === 'compact' && styles.pageCompact, { backgroundColor: theme.colors.canvas }]}>{children}</View>;
}

export function PageHeader({ eyebrow, title, subtitle }: { eyebrow?: string; title: string; subtitle?: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.header}>
      {eyebrow ? <Text style={[styles.eyebrow, { color: theme.colors.moss }]}>{eyebrow}</Text> : null}
      <Text style={[styles.title, { color: theme.colors.ink }]}>{title}</Text>
      {subtitle ? <Text style={[styles.subtitle, { color: theme.colors.muted }]}>{subtitle}</Text> : null}
    </View>
  );
}

export function SectionTitle({ title, action, href }: { title: string; action?: string; href?: any }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.sectionRow}>
      <Text style={[styles.sectionTitle, { color: theme.colors.ink }]}>{title}</Text>
      {action && href ? <Link href={href} style={[styles.link, { color: theme.colors.moss }]}>{action}</Link> : null}
    </View>
  );
}

export function Card({ children, tone, style }: PropsWithChildren<{ tone?: 'moss' | 'amber' | 'plum' | 'blue'; style?: StyleProp<ViewStyle> }>) {
  const theme = useLifeOSTheme();
  const backgroundColor = tone ? ({ moss: theme.colors.mossSoft, amber: theme.colors.amberSoft, plum: theme.colors.plumSoft, blue: theme.colors.blueSoft }[tone]) : theme.colors.paper;
  return <View style={[styles.card, theme.density === 'compact' && styles.cardCompact, { backgroundColor, borderColor: theme.colors.line }, style]}>{children}</View>;
}

export function Pill({ children, tone = 'neutral' }: PropsWithChildren<{ tone?: 'neutral' | 'moss' | 'amber' | 'plum' | 'blue' }>) {
  const theme = useLifeOSTheme();
  const backgroundColor = { neutral: theme.dark ? '#2D3028' : '#ECEBE3', moss: theme.colors.mossSoft, amber: theme.colors.amberSoft, plum: theme.colors.plumSoft, blue: theme.colors.blueSoft }[tone];
  return <View style={[styles.pill, { backgroundColor }]}><Text style={[styles.pillText, { color: theme.colors.ink }]}>{children}</Text></View>;
}

export function ActionButton({ label, icon, onPress, quiet, disabled }: { label: string; icon?: string; onPress?: () => void; quiet?: boolean; disabled?: boolean }) {
  const theme = useLifeOSTheme();
  return (
    <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={({ pressed }) => [
      styles.button,
      theme.density === 'compact' && styles.buttonCompact,
      { backgroundColor: theme.colors.ink },
      quiet && [styles.buttonQuiet, { backgroundColor: theme.colors.paper, borderColor: theme.colors.line }],
      disabled && { opacity: 0.4 },
      pressed && { opacity: 0.68 },
    ]}>
      {icon ? <Text style={[styles.buttonIcon, { color: quiet ? theme.colors.ink : theme.colors.paper }]}>{icon}</Text> : null}
      <Text style={[styles.buttonText, { color: theme.colors.paper }, quiet && { color: theme.colors.ink }]}>{label}</Text>
    </Pressable>
  );
}

export function Metric({ value, label, footnote }: { value: string; label: string; footnote?: string }) {
  const theme = useLifeOSTheme();
  return (
    <Card style={styles.metric}>
      <Text style={[styles.metricValue, { color: theme.colors.ink }]}>{value}</Text>
      <Text style={[styles.metricLabel, { color: theme.colors.ink }]}>{label}</Text>
      {footnote ? <Text style={[styles.metricFoot, { color: theme.colors.muted }]}>{footnote}</Text> : null}
    </Card>
  );
}

export function Row({ icon, title, detail, trailing, href }: { icon?: string; title: string; detail?: string; trailing?: ReactNode; href?: any }) {
  const theme = useLifeOSTheme();
  const content = (
    <View style={[styles.row, theme.density === 'compact' && styles.rowCompact, { borderBottomColor: theme.colors.line }]}>
      {icon ? <View style={[styles.rowIcon, { backgroundColor: theme.dark ? '#2D3028' : '#EEEDE5' }]}><Text>{icon}</Text></View> : null}
      <View style={styles.rowCopy}>
        <Text style={[styles.rowTitle, { color: theme.colors.ink }]}>{title}</Text>
        {detail ? <Text style={[styles.rowDetail, { color: theme.colors.muted }]}>{detail}</Text> : null}
      </View>
      {trailing ?? (href ? <Text style={[styles.chevron, { color: theme.colors.muted }]}>›</Text> : null)}
    </View>
  );
  return href ? <Link href={href} asChild><Pressable>{content}</Pressable></Link> : content;
}

export const sharedStyles = StyleSheet.create({
  content: { width: '100%', maxWidth: 1080, alignSelf: 'center', paddingHorizontal: 18, paddingBottom: 44, boxSizing: 'border-box' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  body: { color: colors.ink, fontSize: 15, lineHeight: 22 },
  muted: { color: colors.muted, fontSize: 13, lineHeight: 19 },
});

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.canvas },
  pageCompact: {},
  header: { paddingTop: 12, paddingBottom: 22 },
  eyebrow: { color: colors.moss, fontSize: 12, fontWeight: '800', letterSpacing: 1.1, textTransform: 'uppercase', marginBottom: 7 },
  title: { color: colors.ink, fontSize: 34, lineHeight: 39, fontWeight: '800', letterSpacing: -1.2 },
  subtitle: { color: colors.muted, fontSize: 15, lineHeight: 22, marginTop: 7, maxWidth: 620 },
  sectionRow: { marginTop: 24, marginBottom: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitle: { color: colors.ink, fontSize: 18, fontWeight: '700', letterSpacing: -0.3 },
  link: { color: colors.moss, fontSize: 13, fontWeight: '700' },
  card: { borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, padding: 16, ...shadow },
  cardCompact: { padding: 12, borderRadius: radius.sm },
  pill: { alignSelf: 'flex-start', paddingHorizontal: 9, paddingVertical: 5, borderRadius: radius.pill },
  pillText: { color: colors.ink, fontSize: 11, fontWeight: '700' },
  button: { minHeight: 44, borderRadius: radius.pill, backgroundColor: colors.ink, paddingHorizontal: 17, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  buttonCompact: { minHeight: 38, paddingHorizontal: 14 },
  buttonQuiet: { backgroundColor: colors.paper, borderWidth: 1, borderColor: colors.line },
  buttonText: { color: '#FFF', fontSize: 14, fontWeight: '700' },
  buttonQuietText: { color: colors.ink },
  buttonIcon: { fontSize: 16, color: '#FFF' },
  metric: { minWidth: 145, flexGrow: 1, flexBasis: 0 },
  metricValue: { color: colors.ink, fontSize: 26, fontWeight: '800', letterSpacing: -0.8 },
  metricLabel: { color: colors.ink, fontSize: 13, fontWeight: '700', marginTop: 4 },
  metricFoot: { color: colors.muted, fontSize: 11, marginTop: 3 },
  row: { minHeight: 66, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  rowCompact: { minHeight: 54 },
  rowIcon: { width: 36, height: 36, borderRadius: 12, backgroundColor: '#EEEDE5', alignItems: 'center', justifyContent: 'center' },
  rowCopy: { flex: 1, minWidth: 0 },
  rowTitle: { color: colors.ink, fontSize: 15, fontWeight: '700' },
  rowDetail: { color: colors.muted, fontSize: 12, marginTop: 3 },
  chevron: { color: colors.muted, fontSize: 26, fontWeight: '300' },
});
