import { Stack, useRouter } from 'expo-router';
import { Linking, Platform } from 'react-native';
import { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';

import { LifeOSDatabaseProvider } from '@/src/db/provider';
import { setActiveDomainOverride } from '@/src/domain/catalog';
import { useIncomingShareSafe } from '@/src/platform/incoming-share';
import { defaultLifeOSSettings, loadLifeOSSettings, subscribeLifeOSSettings } from '@/src/settings/lifeos-settings';

function IncomingShareRouter() {
  const router = useRouter();
  const incomingShare = useIncomingShareSafe();

  useEffect(() => {
    const subscription = Linking.addEventListener('url', ({ url }) => {
      try {
        if (new URL(url).hostname !== 'expo-sharing') return;
        incomingShare.refreshSharePayloads();
        setTimeout(() => {
          router.push('/capture?incomingShare=1');
        }, 0);
      } catch {
        // Ignore unrelated or malformed links.
      }
    });
    return () => subscription.remove();
  }, [incomingShare.refreshSharePayloads, router]);

  return null;
}

export default function RootLayout() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void loadLifeOSSettings().then((settings) => {
      if (cancelled) return;
      setActiveDomainOverride(settings.runtime.activeDomain);
      setReady(true);
    }).catch(() => {
      if (!cancelled) setReady(true);
    });
    const unsubscribe = subscribeLifeOSSettings((settings) => {
      setActiveDomainOverride(settings.runtime.activeDomain);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  if (!ready) return null;

  return (
    <LifeOSDatabaseProvider seedInDev={__DEV__}>
      <StatusBar style={defaultLifeOSSettings.runtime.theme === 'dark' ? 'light' : 'dark'} />
      {Platform.OS === 'web' ? null : <IncomingShareRouter />}
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="record/[id]" />
        <Stack.Screen name="collection/[id]" />
        <Stack.Screen name="search" />
        <Stack.Screen name="capture" />
        <Stack.Screen name="system" />
        <Stack.Screen name="config" />
        <Stack.Screen name="health-diagnostics" />
        <Stack.Screen name="+not-found" />
      </Stack>
    </LifeOSDatabaseProvider>
  );
}
