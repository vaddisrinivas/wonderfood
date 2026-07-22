import { Platform } from 'react-native';

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
