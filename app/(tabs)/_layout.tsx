import { Tabs } from 'expo-router';
import { StyleSheet, Text } from 'react-native';

import { colors } from '@/src/theme';

const icons: Record<string, string> = { index: '⌂', food: '◉', chat: '✦' };

export default function TabLayout() {
  return (
    <Tabs screenOptions={({ route }) => ({
      headerStyle: { backgroundColor: colors.canvas },
      headerShadowVisible: false,
      headerTintColor: colors.ink,
      headerTitleStyle: { fontWeight: '800' },
      tabBarStyle: styles.tabBar,
      tabBarActiveTintColor: colors.moss,
      tabBarInactiveTintColor: colors.muted,
      tabBarLabelStyle: styles.tabLabel,
      tabBarIcon: ({ color }) => <Text style={[styles.tabIcon, { color }]}>{icons[route.name] ?? '•'}</Text>,
    })}>
      <Tabs.Screen name="index" options={{ title: 'Today', headerShown: false }} />
      <Tabs.Screen name="food" options={{ title: 'Food', headerShown: false }} />
      <Tabs.Screen name="chat" options={{ title: 'Chat', headerShown: false }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  tabBar: { backgroundColor: colors.paper, borderTopColor: colors.line, height: 76, paddingTop: 7, paddingBottom: 8 },
  tabLabel: { fontSize: 11, fontWeight: '700' },
  tabIcon: { fontSize: 23, fontWeight: '700', height: 26 },
});
