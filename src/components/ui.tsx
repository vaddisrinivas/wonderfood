import { PropsWithChildren, ReactNode } from 'react';
import { Pressable, StyleProp, StyleSheet, Text, View, ViewStyle } from 'react-native';
import { Link } from 'expo-router';

import { colors, radius, shadow } from '@/src/theme';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

export function Page({ children }: PropsWithChildren) {
  const { runtime } = useLifeOSSettingsSnapshot();
  return <View style={[styles.page, runtime.density === 'compact' && styles.pageCompact]}>{children}</View>;
}

export function PageHeader({ eyebrow, title, subtitle }: { eyebrow?: string; title: string; subtitle?: string }) {
  return (
    <View style={styles.header}>
      {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
      <Text style={styles.title}>{title}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
    </View>
  );
}

export function SectionTitle({ title, action, href }: { title: string; action?: string; href?: any }) {
  return (
    <View style={styles.sectionRow}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {action && href ? <Link href={href} style={styles.link}>{action}</Link> : null}
    </View>
  );
}

export function Card({ children, tone, style }: PropsWithChildren<{ tone?: 'moss' | 'amber' | 'plum' | 'blue'; style?: StyleProp<ViewStyle> }>) {
  const { runtime } = useLifeOSSettingsSnapshot();
  const backgroundColor = tone ? ({ moss: colors.mossSoft, amber: colors.amberSoft, plum: colors.plumSoft, blue: colors.blueSoft }[tone]) : colors.paper;
  return <View style={[styles.card, runtime.density === 'compact' && styles.cardCompact, { backgroundColor }, style]}>{children}</View>;
}

export function Pill({ children, tone = 'neutral' }: PropsWithChildren<{ tone?: 'neutral' | 'moss' | 'amber' | 'plum' | 'blue' }>) {
  const backgroundColor = { neutral: '#ECEBE3', moss: colors.mossSoft, amber: colors.amberSoft, plum: colors.plumSoft, blue: colors.blueSoft }[tone];
  return <View style={[styles.pill, { backgroundColor }]}><Text style={styles.pillText}>{children}</Text></View>;
}

export function ActionButton({ label, icon, onPress, quiet, disabled }: { label: string; icon?: string; onPress?: () => void; quiet?: boolean; disabled?: boolean }) {
  const { runtime } = useLifeOSSettingsSnapshot();
  return (
    <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={({ pressed }) => [styles.button, runtime.density === 'compact' && styles.buttonCompact, quiet && styles.buttonQuiet, disabled && { opacity: 0.4 }, pressed && { opacity: 0.68 }]}>
      {icon ? <Text style={styles.buttonIcon}>{icon}</Text> : null}
      <Text style={[styles.buttonText, quiet && styles.buttonQuietText]}>{label}</Text>
    </Pressable>
  );
}

export function Metric({ value, label, footnote }: { value: string; label: string; footnote?: string }) {
  return (
    <Card style={styles.metric}>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
      {footnote ? <Text style={styles.metricFoot}>{footnote}</Text> : null}
    </Card>
  );
}

export function Row({ icon, title, detail, trailing, href }: { icon?: string; title: string; detail?: string; trailing?: ReactNode; href?: any }) {
  const { runtime } = useLifeOSSettingsSnapshot();
  const content = (
    <View style={[styles.row, runtime.density === 'compact' && styles.rowCompact]}>
      {icon ? <View style={styles.rowIcon}><Text>{icon}</Text></View> : null}
      <View style={styles.rowCopy}>
        <Text style={styles.rowTitle}>{title}</Text>
        {detail ? <Text style={styles.rowDetail}>{detail}</Text> : null}
      </View>
      {trailing ?? (href ? <Text style={styles.chevron}>›</Text> : null)}
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
