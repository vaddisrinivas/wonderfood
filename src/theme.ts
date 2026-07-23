import { Platform, useColorScheme } from 'react-native';

import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

export const colors = {
  ink: '#171914',
  muted: '#666A5F',
  canvas: '#F5F4EC',
  paper: '#FFFEF8',
  line: '#E2E0D4',
  moss: '#536B45',
  mossSoft: '#E4EBDD',
  plum: '#664961',
  plumSoft: '#EEE4ED',
  amber: '#B86C32',
  amberSoft: '#F6E6D5',
  blue: '#3D6072',
  blueSoft: '#E1EBEF',
  red: '#9A4F46',
};

export const radius = { sm: 10, md: 16, lg: 24, pill: 999 };

export const shadow = Platform.select({
  web: { boxShadow: '0 6px 16px rgba(39, 41, 31, 0.08)' },
  default: {
    shadowColor: '#27291F',
    shadowOpacity: 0.08,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
}) ?? {};

export const darkColors = {
  ink: '#F4F0E6',
  muted: '#B9B2A3',
  canvas: '#11130F',
  paper: '#191B16',
  line: '#34372D',
  moss: '#A9C891',
  mossSoft: '#263220',
  plum: '#D9AFD0',
  plumSoft: '#342637',
  amber: '#F0B173',
  amberSoft: '#3C291B',
  blue: '#9FC7D8',
  blueSoft: '#1F3138',
  red: '#E28E85',
};

export type LifeOSColors = typeof colors;

export function useLifeOSTheme() {
  const { runtime } = useLifeOSSettingsSnapshot();
  const system = useColorScheme();
  const dark = runtime.theme === 'dark' || (runtime.theme === 'system' && system === 'dark');
  return {
    colors: dark ? darkColors : colors,
    dark,
    density: runtime.density,
  };
}
