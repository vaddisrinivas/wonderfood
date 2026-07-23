import { Tabs } from 'expo-router';
import { StyleSheet, Text } from 'react-native';

import { colors } from '@/src/theme';
import { loadCatalog } from '@/src/domain/catalog';

const icons: Record<string, string> = {
  index: '⌂',
  food: '◉',
  chat: '✦',
  sources: '▣',
  settings: '⚙',
};

export default function TabLayout() {
  const domain = loadCatalog().activeManifest;
  return (
    <Tabs screenOptions={({ route }) => ({
      headerStyle: { backgroundColor: colors.canvas },
      headerShadowVisible: false,
      headerTintColor: colors.ink,
      headerTitleStyle: { fontWeight: '800' },
      tabBarStyle: styles.tabBar,
      tabBarItemStyle: styles.tabItem,
      tabBarActiveTintColor: colors.moss,
      tabBarInactiveTintColor: colors.muted,
      tabBarLabelStyle: styles.tabLabel,
      tabBarIcon: ({ color }) => <Text style={[styles.tabIcon, { color }]}>{icons[route.name] ?? '•'}</Text>,
    })}>
      <Tabs.Screen name="index" options={{ title: 'Home', headerShown: false }} />
      <Tabs.Screen name="food" options={{ title: domain.label, headerShown: false }} />
      <Tabs.Screen name="chat" options={{ title: 'Chat', headerShown: false }} />
      <Tabs.Screen name="sources" options={{ title: 'Sources', headerShown: false }} />
      <Tabs.Screen name="settings" options={{ title: 'Set', headerShown: false }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  tabBar: { backgroundColor: colors.paper, borderTopColor: colors.line, height: 76, paddingTop: 7, paddingBottom: 8 },
  tabItem: { minWidth: 0, paddingHorizontal: 0 },
  tabLabel: { fontSize: 8, fontWeight: '800' },
  tabIcon: { fontSize: 17, fontWeight: '800', height: 23 },
});
