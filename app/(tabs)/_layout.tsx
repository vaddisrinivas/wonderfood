import { Tabs } from 'expo-router';
import { StyleSheet, Text } from 'react-native';

import { useLifeOSTheme } from '@/src/theme';
import { loadCatalog, setActiveDomainOverride } from '@/src/domain/catalog';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

const icons: Record<string, string> = {
  index: '⌂',
  food: '◉',
  chat: '✦',
  sources: '▣',
  settings: '⚙',
};

export default function TabLayout() {
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const domain = loadCatalog().activeManifest;
  const theme = useLifeOSTheme();
  return (
    <Tabs screenOptions={({ route }) => ({
      headerStyle: { backgroundColor: theme.colors.canvas },
      headerShadowVisible: false,
      headerTintColor: theme.colors.ink,
      headerTitleStyle: { fontWeight: '800' },
      tabBarStyle: [
        styles.tabBar,
        theme.density === 'compact' && styles.tabBarCompact,
        { backgroundColor: theme.colors.paper, borderTopColor: theme.colors.line },
      ],
      tabBarItemStyle: [styles.tabItem, theme.density === 'compact' && styles.tabItemCompact],
      tabBarActiveTintColor: theme.colors.moss,
      tabBarInactiveTintColor: theme.colors.muted,
      tabBarLabelStyle: [styles.tabLabel, theme.density === 'compact' && styles.tabLabelCompact],
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
  tabBar: { height: 76, paddingTop: 7, paddingBottom: 8 },
  tabBarCompact: { height: 62, paddingTop: 4, paddingBottom: 5 },
  tabItem: { minWidth: 0, paddingHorizontal: 0 },
  tabItemCompact: { paddingVertical: 0 },
  tabLabel: { fontSize: 8, fontWeight: '800' },
  tabLabelCompact: { fontSize: 7 },
  tabIcon: { fontSize: 17, fontWeight: '800', height: 23 },
});
