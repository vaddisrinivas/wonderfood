import { Link, Stack } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';

import { colors } from '@/src/theme';
import { LifeOSDatabaseProvider } from '@/src/db/provider';

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
          <Stack.Screen name="sources" options={{ title: 'Sources & sync', headerRight: undefined }} />
          <Stack.Screen name="+not-found" options={{ title: 'Not found', headerRight: undefined }} />
        </Stack>
      </>
    </LifeOSDatabaseProvider>
  );
}

const styles = StyleSheet.create({
  actions: { flexDirection: 'row', alignItems: 'center', gap: 18, marginRight: 8 },
  icon: { color: colors.ink, fontSize: 27, lineHeight: 32, fontWeight: '400' },
  avatar: { width: 31, height: 31, borderRadius: 16, overflow: 'hidden', textAlign: 'center', textAlignVertical: 'center', backgroundColor: colors.ink, color: '#FFF', fontSize: 11, fontWeight: '800', lineHeight: 31 },
});
