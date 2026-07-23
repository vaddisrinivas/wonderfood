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
  const sections = orderedSections(healthConfig.sectionOrder, HEALTH_SECTIONS);

  const renderSection = (section: HealthSection) => {
    switch (section) {
      case 'hero':
        return healthConfig.showHero ? (
          <PageHeader
            key={section}
            eyebrow="LIFEOS / HEALTH CONNECT"
            title="Health Connect status"
            subtitle="Checks whether LifeOS can write, read and remove a tiny temporary hydration entry without keeping health data."
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
              {check?.message ?? 'Checking Health Connect write/read/delete access…'}
            </Text>
          </Card>
        ) : null;
      case 'details':
        return healthConfig.showDetails && check ? (
          <Card key={section} style={styles.card}>
            <Text style={[styles.kicker, { color: theme.colors.muted }]}>device receipt</Text>
            <View style={styles.grid}>
              {healthConfig.showTechnicalReceipt ? (
                <Text style={[styles.machineLine, { color: theme.colors.moss }]}>
                  {`HC_ROUNDTRIP status=${check.status} before=${check.readBeforeDelete} after=${check.readAfterDelete}`}
                </Text>
              ) : null}
              <Fact label="Temporary record" value={check.clientRecordId} />
              <Fact label="Inserted rows" value={String(check.insertedIds.length)} />
              <Fact label="Before cleanup" value={String(check.readBeforeDelete)} />
              <Fact label="After cleanup" value={String(check.readAfterDelete)} />
              <Fact label="Checked at" value={check.observedAt} />
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
  grid: { gap: 10 },
  fact: { gap: 4 },
  machineLine: { fontSize: 13, fontWeight: '900' },
  factLabel: { fontSize: 11, fontWeight: '900', letterSpacing: 1.2, textTransform: 'uppercase' },
  factValue: { fontSize: 14, fontWeight: '800' },
});
