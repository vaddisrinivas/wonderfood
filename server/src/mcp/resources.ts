import { existsSync, readFileSync } from 'node:fs';
import { extname, join } from 'node:path';
import { loadCatalog } from '@/src/domain/catalog';
import { findWorkflow, getActionEvent, listActionEvents, listActionUris, listRecordUris, listRecords, listWorkflows } from './state';
import { listConversations } from '../conversations';

export type McpResource = {
  uri: string;
  name: string;
  mimeType: string;
};

type McpResourceRecord = {
  uri: string;
  name: string;
  mimeType: string;
  path: string | undefined;
  mode: 'file' | 'dynamic';
};

const PROJECT_ROOT = process.cwd();

function resolvePath(relativePath: string) {
  return join(PROJECT_ROOT, relativePath);
}

const STATIC_RESOURCES: Record<string, McpResourceRecord> = {
  'wonderfood://skill/bundled-food': {
    uri: 'wonderfood://skill/bundled-food',
    name: 'bundled-food',
    mimeType: 'text/markdown',
    path: resolvePath('packages/domain-config/skills/food.md'),
    mode: 'file',
  },
  'wonderfood://lifeos/domain-catalog-v1': {
    uri: 'wonderfood://lifeos/domain-catalog-v1',
    name: 'domain-catalog-v1',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/domain-catalog.v1.json'),
    mode: 'file',
  },
  'wonderfood://domain-catalog': {
    uri: 'wonderfood://domain-catalog',
    name: 'domain-catalog',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/domain-catalog.v1.json'),
    mode: 'file',
  },
  'wonderfood://schema/command.v1': {
    uri: 'wonderfood://schema/command.v1',
    name: 'command-v1',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/schemas/command.v1.schema.json'),
    mode: 'file',
  },
  'wonderfood://schema/action-event.v1': {
    uri: 'wonderfood://schema/action-event.v1',
    name: 'action-event-v1',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/schemas/action-event.v1.schema.json'),
    mode: 'file',
  },
  'wonderfood://schema/undo-v1': {
    uri: 'wonderfood://schema/undo-v1',
    name: 'undo-v1',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/schemas/undo.v1.schema.json'),
    mode: 'file',
  },
  'wonderfood://schema/workflow.v1': {
    uri: 'wonderfood://schema/workflow.v1',
    name: 'workflow-v1',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/schemas/workflow.v1.schema.json'),
    mode: 'file',
  },
  'wonderfood://schema/domain-catalog-v1': {
    uri: 'wonderfood://schema/domain-catalog-v1',
    name: 'domain-catalog-v1-schema',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/schemas/domain-catalog.v1.schema.json'),
    mode: 'file',
  },
  'wonderfood://schema/domain.v1': {
    uri: 'wonderfood://schema/domain.v1',
    name: 'domain-v1-schema',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/schemas/domain.v1.schema.json'),
    mode: 'file',
  },
  'wonderfood://schema/proposal-package-v1': {
    uri: 'wonderfood://schema/proposal-package-v1',
    name: 'proposal-package-v1',
    mimeType: 'application/json',
    path: resolvePath('docs/ai/proposal-package.schema.v1.json'),
    mode: 'file',
  },
  'wonderfood://schema/command-envelope-v1': {
    uri: 'wonderfood://schema/command-envelope-v1',
    name: 'command-envelope-v1',
    mimeType: 'application/json',
    path: resolvePath('docs/ai/command-envelope.schema.v1.json'),
    mode: 'file',
  },
  'wonderfood://contract/app-command': {
    uri: 'wonderfood://contract/app-command',
    name: 'app-command',
    mimeType: 'text/markdown',
    path: resolvePath('docs/app-command-contract.md'),
    mode: 'file',
  },
  'wonderfood://manifest/food': {
    uri: 'wonderfood://manifest/food',
    name: 'manifest-food',
    mimeType: 'application/json',
    path: resolvePath('packages/domain-config/domains/food.v1.json'),
    mode: 'file',
  },
};

function resolveMimeType(path: string, fallback: string) {
  return fallback ?? (extname(path) === '.json' ? 'application/json' : 'text/markdown');
}

function getConversationUris(): string[] {
  try {
    const catalog = loadCatalog();
    return catalog.catalog.domains
      .map((entry) => `wonderfood://domain/${entry.id}`)
      .concat(catalog.catalog.domains.flatMap((entry) => [`wonderfood://catalog/domain/${entry.id}`]));
  } catch {
    return ['wonderfood://domain/food'];
  }
}

function getWorkflowUris(): string[] {
  return listWorkflows().map((workflow) => `wonderfood://workflow/${encodeURIComponent(workflow.id)}`);
}

function getRecordUris(): string[] {
  return listRecordUris();
}

function getActionUris(): string[] {
  return listActionUris();
}

export function getMcpResourceUris(): string[] {
  const staticUris = Object.keys(STATIC_RESOURCES);
  return [
    ...staticUris,
    'wonderfood://records',
    'wonderfood://actions',
    'wonderfood://workflows',
    'wonderfood://conversations',
    ...getWorkflowUris(),
    ...getConversationUris(),
    ...getRecordUris(),
    ...getActionUris(),
  ].sort();
}

export function listMcpResources(): McpResource[] {
  const static = Object.values(STATIC_RESOURCES).map((resource) => ({
    uri: resource.uri,
    name: resource.name,
    mimeType: resource.mimeType,
  }));

  const workflows = listWorkflows().map((workflow) => ({
    uri: `wonderfood://workflow/${encodeURIComponent(workflow.id)}`,
    name: `workflow-${workflow.id}`,
    mimeType: 'application/json',
  }));

  const recordList = listRecordUris().map((uri) => ({
    uri,
    name: `record-${decodeURIComponent(uri.replace('wonderfood://record/', ''))}`,
    mimeType: 'application/json',
  }));

  const actionList = listActionUris().map((uri) => ({
    uri,
    name: `action-${decodeURIComponent(uri.replace('wonderfood://action/', ''))}`,
    mimeType: 'application/json',
  }));

  const catalog = {
    uri: 'wonderfood://records',
    name: 'records-index',
    mimeType: 'application/json',
  };

  return [
    ...static,
    catalog,
    { uri: 'wonderfood://actions', name: 'actions-index', mimeType: 'application/json' },
    { uri: 'wonderfood://workflows', name: 'workflows-index', mimeType: 'application/json' },
    { uri: 'wonderfood://conversations', name: 'conversation-index', mimeType: 'application/json' },
    ...workflows,
    ...recordList,
    ...actionList,
    ...getConversationUris().map((uri) => ({ uri, name: uri.split('/').pop() || 'conversation', mimeType: 'application/json' })),
  ].sort((a, b) => a.uri.localeCompare(b.uri));
}

function isConversationCatalogUri(uri: string): boolean {
  return uri.startsWith('wonderfood://domain/') || uri.startsWith('wonderfood://catalog/domain/');
}

function readDomainCatalog(): string {
  const catalog = loadCatalog();
  return JSON.stringify(catalog, null, 2);
}

function readActionActionEvent(actionUri: string) {
  const actionId = decodeURIComponent(actionUri.replace('wonderfood://action/', ''));
  const event = getActionEvent(actionId);
  if (!event) {
    throw new Error(`Unknown action resource: ${actionUri}`);
  }
  return JSON.stringify(event, null, 2);
}

function readConversationResource(uri: string) {
  const catalog = loadCatalog();
  const normalized = uri.replace('wonderfood://catalog/domain/', '').replace('wonderfood://domain/', '');
  const manifest = catalog.domainsById[normalized]?.manifest;
  if (!manifest) {
    throw new Error(`Unknown domain resource: ${uri}`);
  }
  return JSON.stringify({ domain: normalized, manifest }, null, 2);
}

function readRecordResource(uri: string) {
  const recordId = decodeURIComponent(uri.replace('wonderfood://record/', ''));
  const records = listRecords({ query: `"${recordId}"`, limit: 200, includeArchived: true }).filter((record) => record.id === recordId);
  if (!records.length) {
    throw new Error(`Unknown record resource: ${uri}`);
  }
  return JSON.stringify(records[0], null, 2);
}

function readRecordsIndex() {
  const records = listRecords({ includeArchived: true, limit: 500 });
  return JSON.stringify({ type: 'record-index', count: records.length, records }, null, 2);
}

function readWorkflowResource(uri: string) {
  const workflowId = decodeURIComponent(uri.replace('wonderfood://workflow/', ''));
  const workflow = findWorkflow(workflowId);
  if (!workflow) {
    throw new Error(`Unknown workflow resource: ${uri}`);
  }
  return JSON.stringify(workflow, null, 2);
}

function readWorkflowsIndex() {
  return JSON.stringify({ type: 'workflow-index', workflows: listWorkflows() }, null, 2);
}

function readActionsIndex() {
  return JSON.stringify({ type: 'action-index', events: listActionEvents() }, null, 2);
}

function readConversationsIndex() {
  try {
    return JSON.stringify({ type: 'conversation-index', threads: listConversations() }, null, 2);
  } catch {
    return JSON.stringify({ type: 'conversation-index', threads: [] }, null, 2);
  }
}

export function readMcpResource(uri: string): string {
  if (uri in STATIC_RESOURCES) {
    const resource = STATIC_RESOURCES[uri];
    if (resource.mode === 'file') {
      if (!resource.path || !existsSync(resource.path)) {
        throw new Error(`Missing resource file for ${uri}`);
      }
      return readFileSync(resource.path, 'utf-8');
    }
  }

  if (uri === 'wonderfood://records') {
    return readRecordsIndex();
  }

  if (uri === 'wonderfood://actions') {
    return readActionsIndex();
  }

  if (uri === 'wonderfood://workflows') {
    return readWorkflowsIndex();
  }

  if (uri === 'wonderfood://conversations') {
    return readConversationsIndex();
  }

  if (uri === 'wonderfood://domain-catalog') {
    return readDomainCatalog();
  }

  if (uri.startsWith('wonderfood://record/')) {
    return readRecordResource(uri);
  }

  if (uri.startsWith('wonderfood://action/')) {
    return readActionActionEvent(uri);
  }

  if (uri.startsWith('wonderfood://workflow/')) {
    return readWorkflowResource(uri);
  }

  if (isConversationCatalogUri(uri)) {
    return readConversationResource(uri);
  }

  throw new Error(`Unknown resource: ${uri}`);
}

export function resolveResourceMimeType(uri: string): string {
  const resource = STATIC_RESOURCES[uri];
  if (resource?.path && resource.path) {
    return resolveMimeType(resource.path, resource.mimeType);
  }
  return 'application/json';
}
