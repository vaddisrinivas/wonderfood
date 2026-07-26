import { Tabs } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { useLifeOSTheme } from '@/src/theme';
import { loadCatalog, setActiveDomainOverride, VisualToken } from '@/src/domain/catalog';
import { mergeVisualIdentity, visualGlyph } from '@/src/domain/visual-identity';
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
    <Tabs
      detachInactiveScreens={false}
      tabBar={({ state, descriptors, navigation, insets }) => (
        <View style={[
          styles.tabBar,
          theme.density === 'compact' && styles.tabBarCompact,
          {
            backgroundColor: theme.colors.paper,
            borderTopColor: theme.colors.line,
            paddingBottom: Math.max(insets.bottom, 5),
          },
        ]}>
          {state.routes.filter((route) => route.name !== 'sources').map((route) => {
            const focused = state.index === state.routes.indexOf(route);
            const options = descriptors[route.key]?.options;
            const visual = icons[route.name] ?? { fallback: '•' };
            const label = typeof options?.title === 'string' ? options.title : route.name;
            return (
              <Pressable
                key={route.key}
                accessibilityRole="tab"
                accessibilityState={{ selected: focused }}
                accessibilityLabel={`${label} tab`}
                onPress={() => {
                  const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
                  if (!focused && !event.defaultPrevented) navigation.navigate(route.name, route.params);
                }}
                style={styles.tabItem}>
                <Text
                  allowFontScaling={false}
                  style={[
                    styles.tabIconGlyph,
                    {
                      color: focused ? theme.colors.moss : theme.colors.muted,
                      backgroundColor: focused ? theme.colors.mossSoft : 'transparent',
                    },
                  ]}>
                  {visualGlyph(visual.token, visual.fallback)}
                </Text>
                <Text
                  maxFontSizeMultiplier={1.15}
                  style={[
                    styles.tabLabel,
                    theme.density === 'compact' && styles.tabLabelCompact,
                    { color: focused ? theme.colors.moss : theme.colors.muted },
                  ]}>
                  {label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      )}
      screenOptions={{
      headerStyle: { backgroundColor: theme.colors.canvas },
      headerShadowVisible: false,
      headerTintColor: theme.colors.ink,
      headerTitleStyle: { fontWeight: '800' },
    }}>
      <Tabs.Screen name="index" options={{ title: 'Home', headerShown: false }} />
      <Tabs.Screen name="food" options={{ title: domain.label, headerShown: false }} />
      <Tabs.Screen name="chat" options={{ title: 'Chat', headerShown: false }} />
      <Tabs.Screen name="sources" options={{ title: 'Sources', headerShown: false, href: null }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings', headerShown: false }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  tabBar: { minHeight: 76, paddingTop: 7, borderTopWidth: StyleSheet.hairlineWidth, flexDirection: 'row' },
  tabBarCompact: { minHeight: 62, paddingTop: 4 },
  tabItem: { flex: 1, minWidth: 0, minHeight: 52, alignItems: 'center', justifyContent: 'center', gap: 2 },
  tabLabel: { fontSize: 10, lineHeight: 13, fontWeight: '800', textAlign: 'center' },
  tabLabelCompact: { fontSize: 9 },
  tabIconGlyph: { width: 34, height: 30, borderRadius: 11, fontSize: 15, lineHeight: 30, fontWeight: '900', textAlign: 'center' },
});
