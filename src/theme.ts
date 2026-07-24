import { Platform, useColorScheme } from 'react-native';

import { useLifeOSSettingsSnapshot } from '@/src/settings/lifeos-settings';

export const colors = {
  ink: '#241A13',
  muted: '#806F5A',
  canvas: '#FFF4E3',
  paper: '#FFFDF7',
  line: '#ECD9BE',
  moss: '#2F7D4A',
  mossSoft: '#DFF2D8',
  plum: '#7B3F6A',
  plumSoft: '#F3DDEC',
  amber: '#C76D20',
  amberSoft: '#FFE3B3',
  blue: '#276A84',
  blueSoft: '#D9F0F4',
  red: '#C84932',
};

export const radius = { sm: 10, md: 16, lg: 24, pill: 999 };

export const shadow = Platform.select({
  web: { boxShadow: '0 14px 34px rgba(111, 63, 24, 0.12)' },
  default: {
    shadowColor: '#6F3F18',
    shadowOpacity: 0.12,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 12 },
    elevation: 3,
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
