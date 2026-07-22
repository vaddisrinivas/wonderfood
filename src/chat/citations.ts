import { ChatCitation } from '@/src/chat/types';

export const defaultCitations: ChatCitation[] = [
  {
    label: 'App snapshot',
    detail: 'kitchen graph and local records',
    href: 'wonderfood://app/snapshot',
    tone: 'moss',
  },
  {
    label: 'LifeOS Notion',
    detail: '2026 template + relations',
    href: 'https://app.notion.com',
    tone: 'blue',
  },
  {
    label: 'LifeOS Sheets',
    detail: 'meal and shopping projection',
    href: 'https://docs.google.com/spreadsheets',
    tone: 'amber',
  },
];

export function ensureCitations(items: ChatCitation[] | null | undefined): ChatCitation[] {
  if (!items || items.length === 0) {
    return defaultCitations;
  }

  return items.filter((item) => item.label && item.detail && item.href);
}

export function citationLabel(citation: ChatCitation): string {
  return `${citation.label}: ${citation.detail}`;
}

export type Citation = ChatCitation;
