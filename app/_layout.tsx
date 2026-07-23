import { Link, Stack } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';

import { colors } from '@/src/theme';
import { LifeOSDatabaseProvider } from '@/src/db/provider';
import { setActiveDomainOverride } from '@/src/domain/catalog';
import { loadLifeOSSettings } from '@/src/settings/lifeos-settings';

function HeaderActions() {
  return (
    <View style={styles.actions}>
      <Link href="/search" asChild><Pressable accessibilityLabel="Search"><Text style={styles.icon}>⌕</Text></Pressable></Link>
      <Link href="/capture" asChild><Pressable accessibilityLabel="Capture"><Text style={styles.icon}>＋</Text></Pressable></Link>
      <Link href="/system" asChild><Pressable accessibilityLabel="System settings"><Text style={styles.avatar}>SV</Text></Pressable></Link>
    </View>
  );
}

export default function RootLayout() {
  const [settingsReady, setSettingsReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void loadLifeOSSettings().then((settings) => {
      if (cancelled) return;
      setActiveDomainOverride(settings.runtime.activeDomain);
      setSettingsReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!settingsReady) {
    return <View style={styles.boot}><ActivityIndicator color={colors.moss} /></View>;
  }

  return (
    <LifeOSDatabaseProvider>
      <>
        <StatusBar style="dark" />
        <Stack screenOptions={{
          headerStyle: { backgroundColor: colors.canvas },
          headerShadowVisible: false,
          headerTintColor: colors.ink,
          headerTitleStyle: { fontWeight: '800' },
          contentStyle: { backgroundColor: colors.canvas },
          headerRight: HeaderActions,
        }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="record/[id]" options={{ title: 'Record', headerRight: undefined }} />
          <Stack.Screen name="search" options={{ title: 'Search', presentation: 'modal', headerRight: undefined }} />
          <Stack.Screen name="capture" options={{ title: 'Quick capture', presentation: 'modal', headerRight: undefined }} />
          <Stack.Screen name="system" options={{ title: 'LifeOS system', headerRight: undefined }} />
          <Stack.Screen name="settings" options={{ title: 'Connections', headerRight: undefined }} />
          <Stack.Screen name="config" options={{ title: 'Config Studio', headerRight: undefined }} />
          <Stack.Screen name="sources" options={{ title: 'Sources & sync', headerRight: undefined }} />
          <Stack.Screen name="+not-found" options={{ title: 'Not found', headerRight: undefined }} />
        </Stack>
      </>
    </LifeOSDatabaseProvider>
  );
}

const styles = StyleSheet.create({
  boot: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.canvas },
  actions: { flexDirection: 'row', alignItems: 'center', gap: 18, marginRight: 8 },
  icon: { color: colors.ink, fontSize: 27, lineHeight: 32, fontWeight: '400' },
  avatar: { width: 31, height: 31, borderRadius: 16, overflow: 'hidden', textAlign: 'center', textAlignVertical: 'center', backgroundColor: colors.ink, color: '#FFF', fontSize: 11, fontWeight: '800', lineHeight: 31 },
});
