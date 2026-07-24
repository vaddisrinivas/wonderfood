import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { HealthConnectRoundTripProof, runLifeOSHealthRoundTripProof } from '@/src/health/connect';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';
import { useLifeOSTheme } from '@/src/theme';

const HEALTH_SECTIONS = ['hero', 'status', 'details'] as const;
type HealthSection = typeof HEALTH_SECTIONS[number];

function orderedSections(value: string, defaults: readonly HealthSection[]) {
  const allowed = new Set(defaults);
  const requested = value
    .split(',')
    .map((section) => section.trim())
    .filter((section): section is HealthSection => allowed.has(section as HealthSection));
  const missing = defaults.filter((section) => !requested.includes(section));
  return [...requested, ...missing];
}

function friendlyCheckedAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

export default function HealthDiagnosticsScreen() {
  const theme = useLifeOSTheme();
  const settings = useLifeOSSettingsSnapshot();
  const healthConfig = settings.runtime.surfaceConfig.health;
  const [check, setCheck] = useState<HealthConnectRoundTripProof | null>(null);

  useEffect(() => {
    let cancelled = false;
    void runLifeOSHealthRoundTripProof().then((next) => {
      if (!cancelled) setCheck(next);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const passed = check?.status === 'passed';
  const cleanedUp = (check?.readAfterDelete ?? 0) === 0;
  const sections = orderedSections(healthConfig.sectionOrder, HEALTH_SECTIONS);

  const renderSection = (section: HealthSection) => {
    switch (section) {
      case 'hero':
        return healthConfig.showHero ? (
          <PageHeader
            key={section}
            eyebrow="LIFEOS / HEALTH CONNECT"
            title="Health data access"
            subtitle="Use steps, hydration, nutrition, calories and weight as context for LifeOS. You stay in control of permissions."
          />
        ) : null;
      case 'status':
        return healthConfig.showStatusCard ? (
          <Card key={section} tone={passed ? 'moss' : 'plum'} style={styles.card}>
            <View style={styles.row}>
              <Pill tone={passed ? 'moss' : 'plum'}>{check ? check.status.toUpperCase() : 'RUNNING'}</Pill>
              <Text style={[styles.kicker, { color: theme.colors.muted }]}>local device check</Text>
            </View>
            <Text style={[styles.title, { color: theme.colors.ink }]}>
              {check ? (passed ? 'Health Connect is ready for LifeOS.' : check.message) : 'Checking Health Connect access…'}
            </Text>
            <Text style={[styles.body, { color: theme.colors.muted }]}>
              {passed
                ? 'LifeOS can read allowed health context and clean up its own temporary test entry.'
                : 'Open Health Connect permissions if you want Health to join your Food, planning and recovery context.'}
            </Text>
          </Card>
        ) : null;
      case 'details':
        return healthConfig.showDetails && check ? (
          <Card key={section} style={styles.card}>
            <Text style={[styles.kicker, { color: theme.colors.muted }]}>what this means</Text>
            <View style={styles.grid}>
              <Fact label="Available to LifeOS" value="Hydration, nutrition, steps, calories and weight if you grant them." />
              <Fact
                label="Privacy"
                value={
                  passed
                    ? (cleanedUp ? 'Temporary test data was removed after verification.' : 'Temporary check cleanup needs attention.')
                    : 'No health data was read or stored by this check.'
                }
              />
              <Fact label="Last checked" value={friendlyCheckedAt(check.observedAt)} />
              <Fact label="Next step" value={passed ? 'Use Health as an optional domain context in Chat and Sources.' : 'Grant Health Connect permissions from Settings.'} />
            </View>
          </Card>
        ) : null;
      default:
        return null;
    }
  };

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          {sections.map(renderSection)}
        </View>
      </ScrollView>
    </Page>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  const theme = useLifeOSTheme();
  return (
    <View style={styles.fact}>
      <Text style={[styles.factLabel, { color: theme.colors.muted }]}>{label}</Text>
      <Text style={[styles.factValue, { color: theme.colors.ink }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: 18 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  kicker: { fontSize: 12, fontWeight: '800', letterSpacing: 1.4, textTransform: 'uppercase' },
  title: { fontSize: 24, fontWeight: '900', lineHeight: 30 },
  body: { fontSize: 14, lineHeight: 21 },
  grid: { gap: 10 },
  fact: { gap: 4 },
  factLabel: { fontSize: 11, fontWeight: '900', letterSpacing: 1.2, textTransform: 'uppercase' },
  factValue: { fontSize: 14, fontWeight: '800', lineHeight: 20 },
});
