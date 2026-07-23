import { Tabs } from 'expo-router';
import { StyleSheet } from 'react-native';

import { VisualMark } from '@/src/components/ui';
import { useLifeOSTheme } from '@/src/theme';
import { loadCatalog, setActiveDomainOverride, VisualToken } from '@/src/domain/catalog';
import { mergeVisualIdentity } from '@/src/domain/visual-identity';
import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

export default function TabLayout() {
  const settings = useLifeOSSettingsSnapshot();
  setActiveDomainOverride(settings.runtime.activeDomain);
  const domain = loadCatalog().activeManifest;
  const visualIdentity = mergeVisualIdentity(domain, settings.runtime.visualIdentityOverrides);
  const icons: Record<string, { token?: VisualToken; fallback: string }> = {
    index: { token: visualIdentity.actions?.home, fallback: '⌂' },
    food: { token: visualIdentity.domain, fallback: domain.label.slice(0, 1) },
    chat: { token: visualIdentity.actions?.chat ?? visualIdentity.actions?.ask_with_collection, fallback: '✦' },
    sources: { token: visualIdentity.actions?.open_sources, fallback: '▣' },
    settings: { token: visualIdentity.actions?.settings, fallback: '⚙' },
  };
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
      tabBarIcon: ({ color, focused }) => {
        const visual = icons[route.name] ?? { fallback: '•' };
        return (
          <VisualMark
            token={visual.token}
            fallback={visual.fallback}
            size={26}
            backgroundColor={focused ? theme.colors.mossSoft : 'transparent'}
            color={String(color)}
            label={`${route.name} tab`}
            glyphStyle={styles.tabIconGlyph}
          />
        );
      },
    })}>
      <Tabs.Screen name="index" options={{ title: 'Home', headerShown: false }} />
      <Tabs.Screen name="food" options={{ title: domain.label, headerShown: false }} />
      <Tabs.Screen name="chat" options={{ title: 'Chat', headerShown: false }} />
      <Tabs.Screen name="sources" options={{ title: 'Sources', headerShown: false }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings', headerShown: false }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  tabBar: { height: 76, paddingTop: 7, paddingBottom: 8 },
  tabBarCompact: { height: 62, paddingTop: 4, paddingBottom: 5 },
  tabItem: { minWidth: 0, paddingHorizontal: 0 },
  tabItemCompact: { paddingVertical: 0 },
  tabLabel: { fontSize: 8, fontWeight: '800', minWidth: 54, textAlign: 'center' },
  tabLabelCompact: { fontSize: 7 },
  tabIconGlyph: { fontSize: 15, fontWeight: '900' },
});
