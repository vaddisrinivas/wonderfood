import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, Page, PageHeader, Pill, sharedStyles } from '@/src/components/ui';
import { HealthConnectRoundTripProof, runLifeOSHealthRoundTripProof } from '@/src/health/connect';
import { useLifeOSTheme } from '@/src/theme';

export default function HealthDiagnosticsScreen() {
  const theme = useLifeOSTheme();
  const [proof, setProof] = useState<HealthConnectRoundTripProof | null>(null);

  useEffect(() => {
    let cancelled = false;
    void runLifeOSHealthRoundTripProof().then((next) => {
      if (!cancelled) setProof(next);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const passed = proof?.status === 'passed';

  return (
    <Page>
      <ScrollView contentInsetAdjustmentBehavior="automatic">
        <View style={sharedStyles.content}>
          <PageHeader
            eyebrow="LIFEOS / HEALTH DIAGNOSTICS"
            title="Health Connect round trip"
            subtitle="Disposable Hydration record: write, read, delete, verify gone."
          />
          <Card tone={passed ? 'moss' : 'plum'} style={styles.card}>
            <View style={styles.row}>
              <Pill tone={passed ? 'moss' : 'plum'}>{proof ? proof.status.toUpperCase() : 'RUNNING'}</Pill>
              <Text style={[styles.kicker, { color: theme.colors.muted }]}>native evidence</Text>
            </View>
            <Text style={[styles.title, { color: theme.colors.ink }]}>
              {proof?.message ?? 'Running Health Connect write/read/delete proof…'}
            </Text>
            {proof ? (
              <View style={styles.grid}>
                <Text style={[styles.machineLine, { color: theme.colors.moss }]}>
                  {`HC_ROUNDTRIP status=${proof.status} before=${proof.readBeforeDelete} after=${proof.readAfterDelete}`}
                </Text>
                <Fact label="clientRecordId" value={proof.clientRecordId} />
                <Fact label="insertedIds" value={String(proof.insertedIds.length)} />
                <Fact label="readBeforeDelete" value={String(proof.readBeforeDelete)} />
                <Fact label="readAfterDelete" value={String(proof.readAfterDelete)} />
                <Fact label="observedAt" value={proof.observedAt} />
              </View>
            ) : null}
          </Card>
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
