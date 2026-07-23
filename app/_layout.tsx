import { Link, Stack } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View, useColorScheme } from 'react-native';
import { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';

import { colors, darkColors, LifeOSColors } from '@/src/theme';
import { LifeOSDatabaseProvider } from '@/src/db/provider';
import { setActiveDomainOverride } from '@/src/domain/catalog';
import { LifeOSSettings, defaultLifeOSSettings, loadLifeOSSettings, subscribeLifeOSSettings } from '@/src/settings/lifeos-settings';

function HeaderActions({ palette }: { palette: LifeOSColors }) {
  return (
    <View style={styles.actions}>
      <Link href="/search" asChild><Pressable accessibilityLabel="Search"><Text style={[styles.icon, { color: palette.ink }]}>⌕</Text></Pressable></Link>
      <Link href="/capture" asChild><Pressable accessibilityLabel="Capture"><Text style={[styles.icon, { color: palette.ink }]}>＋</Text></Pressable></Link>
      <Link href="/settings" asChild><Pressable accessibilityLabel="Settings"><Text style={[styles.avatar, { backgroundColor: palette.ink, color: palette.paper }]}>SV</Text></Pressable></Link>
    </View>
  );
}

export default function RootLayout() {
  const systemTheme = useColorScheme();
  const [settingsReady, setSettingsReady] = useState(false);
  const [settings, setSettings] = useState<LifeOSSettings>(defaultLifeOSSettings);

  useEffect(() => {
    let cancelled = false;
    void loadLifeOSSettings().then((settings) => {
      if (cancelled) return;
      setActiveDomainOverride(settings.runtime.activeDomain);
      setSettings(settings);
      setSettingsReady(true);
    });
    const unsubscribe = subscribeLifeOSSettings((settings) => {
      setActiveDomainOverride(settings.runtime.activeDomain);
      setSettings(settings);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  if (!settingsReady) {
    return <View style={styles.boot}><ActivityIndicator color={colors.moss} /></View>;
  }

  const activeDark = settings.runtime.theme === 'dark' || (settings.runtime.theme === 'system' && systemTheme === 'dark');
  const activeColors = activeDark ? darkColors : colors;

  return (
    <LifeOSDatabaseProvider>
      <>
        <StatusBar style={activeDark ? 'light' : 'dark'} />
        <Stack screenOptions={{
          headerStyle: { backgroundColor: activeColors.canvas },
          headerShadowVisible: false,
          headerTintColor: activeColors.ink,
          headerTitleStyle: { fontWeight: '800' },
          contentStyle: { backgroundColor: activeColors.canvas },
          headerRight: () => <HeaderActions palette={activeColors} />,
        }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="record/[id]" options={{ headerShown: false }} />
          <Stack.Screen name="search" options={{ title: 'Search', presentation: 'modal', headerRight: undefined }} />
          <Stack.Screen name="capture" options={{ title: 'Quick capture', presentation: 'modal', headerRight: undefined }} />
          <Stack.Screen name="system" options={{ title: 'LifeOS system', headerRight: undefined }} />
          <Stack.Screen name="config" options={{ title: 'Customize app', headerRight: undefined }} />
          <Stack.Screen name="health-diagnostics" options={{ title: 'Health', headerRight: undefined }} />
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
