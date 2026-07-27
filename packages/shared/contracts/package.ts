import type { QueryPredicate, QuerySort } from './query';

export type FieldType = 'text' | 'number' | 'boolean' | 'timestamp' | 'json';

export type ComputedFieldSpec = {
  id: string;
  collection: string | '*';
  dependsOn: string[];
  expression: unknown;
};

export type CollectionSpec = {
  id: string;
  fields: Record<string, { type: FieldType; required?: boolean; indexed?: boolean }>;
};

export type ViewSpec = {
  id: string;
  query: string;
  mode: 'list' | 'board' | 'table' | 'calendar' | 'timeline' | 'chart';
  fields: string[];
  groupBy?: string;
  layout?: Record<string, unknown>;
};

export type PackageSurfaceSpec = {
  id: string;
  label: string;
  icon?: string;
  imageUrl?: string;
  views?: string[];
  collections: string[];
};

export type PackageUiAction = {
  kind: 'open_url' | 'propose';
  label?: string;
  url?: string;
  command?: string;
  tool?: string;
  payload?: Record<string, unknown>;
};

export type PackageUiComponent = {
  kind: 'recordList' | 'metric' | 'action' | 'text';
  id?: string;
  title?: string;
  subtitle?: string;
  view?: string;
  tone?: 'neutral' | 'moss' | 'amber' | 'plum' | 'blue';
  query?: {
    collections?: string[];
    match?: string;
    limit?: number;
  };
  action?: PackageUiAction;
};

export type PackagePresentationUi = {
  schemaVersion?: 'wonder.ui.v1';
  openUrlAllowlist?: string[];
  components?: PackageUiComponent[];
  screens?: Record<string, { title?: string; subtitle?: string; components?: PackageUiComponent[] }>;
  defaultScreen?: string;
};

export type PackagePresentationSpec = {
  label: string;
  homeSurface?: string;
  surfaces: PackageSurfaceSpec[];
  visualIdentity?: Record<string, unknown>;
  render?: Record<string, unknown>;
  ui?: PackagePresentationUi;
  richDetailSchema?: string;
  providerTemplateFields?: Record<string, unknown>;
  sourceSchemaVersion?: string;
};

export type RuleSpec = {
  id: string;
  trigger: {
    kind: 'operation' | 'schedule' | 'query_transition';
    query?: string;
    transition?: 'enter' | 'leave' | 'change';
  };
  when?: unknown;
  effect: { kind: 'propose_operation'; operation: string | OperationTemplate };
  mode: 'suggest' | 'automatic';
  maxRunsPerEvent: number;
};

export type OperationTemplate = Readonly<
  | { kind: 'custom'; tool: string }
  | { kind: 'create_record'; domain?: string; collection: string; recordId?: string; properties?: Record<string, unknown> }
  | { kind: 'update_record'; domain?: string; collection?: string; recordId: string; expectedRevision?: number; changes: Record<string, unknown> }
  | { kind: 'archive_record'; domain?: string; collection?: string; recordId: string; expectedRevision?: number }
  | { kind: 'restore_record'; domain?: string; collection?: string; recordId: string; expectedRevision?: number }
>;

export type AppPackageV2 = {
  schemaVersion: 'wonder.app-package.v2';
  id: string;
  version: string;
  collections: Record<string, CollectionSpec>;
  queries: Record<string, { from: string; where?: QueryPredicate; orderBy?: QuerySort[]; limit?: number }>;
  views: Record<string, ViewSpec>;
  presentation?: PackagePresentationSpec;
  computedFields?: ComputedFieldSpec[];
  rules: RuleSpec[];
  capabilities: string[];
  acceptanceTests: string[];
};

export type PackageValidation = { valid: true; package: AppPackageV2 } | { valid: false; errors: string[] };
