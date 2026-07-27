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

export type A2UiAction = {
  kind: 'open_url' | 'propose';
  label?: string;
  url?: string;
  command?: string;
  tool?: string;
  payload?: Record<string, unknown>;
};

export type A2UiComponent = {
  kind: 'recordList' | 'metric' | 'action' | 'text' | 'widget';
  id?: string;
  title?: string;
  subtitle?: string;
  widget?:
    | 'assistantChat'
    | 'healthConnect'
    | 'schemaEditor'
    | 'widgetCatalog'
    | 'postCard'
    | 'pollCard'
    | 'linkPreview'
    | 'feedList'
    | 'kanbanBoard'
    | 'chartBlock'
    | 'mediaBlock'
    | 'mapBlock'
    | 'permissionCard'
    | 'providerStatus'
    | 'themePreview';
  props?: Record<string, unknown>;
  view?: string;
  tone?: 'neutral' | 'moss' | 'amber' | 'plum' | 'blue';
  query?: {
    collections?: string[];
    match?: string;
    limit?: number;
  };
  action?: A2UiAction;
};

export type A2UiSurface = {
  schemaVersion?: 'a2ui.v0_9';
  openUrlAllowlist?: string[];
  components?: A2UiComponent[];
  screens?: Record<string, { title?: string; subtitle?: string; components?: A2UiComponent[] }>;
  defaultScreen?: string;
};

export type PackagePresentationSpec = {
  label: string;
  homeSurface?: string;
  surfaces: PackageSurfaceSpec[];
  visualIdentity?: Record<string, unknown>;
  render?: Record<string, unknown>;
  ui?: A2UiSurface;
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

export type AppPackageDependencyPin = {
  package: string;
  version: string;
  source?: 'npm' | 'maven' | 'gradle' | 'cocoapods' | 'other';
};

export type AppPackageNativeCapability = {
  schemaVersion: 'wonder.app-package-native-capabilities.v1';
  platform: 'expo' | 'android' | 'ios' | 'web';
  packages: string[];
  permissions?: Array<string | {
    id: string;
    platform: 'expo' | 'android' | 'ios' | 'web';
    permission: string;
    reason: string;
    required?: boolean;
    prompt?: string;
  }>;
};

export type AppPackageContractLock = {
  schemaVersion: 'wonder.package-contract-lock.v1';
  algorithm: 'sha256';
  checksum: string;
  pinnedAt: string;
  dependencyPins: AppPackageDependencyPin[];
  nativeCapabilities: AppPackageNativeCapability;
};

export type AppPackageV3 = {
  schemaVersion: 'wonder.app-package.v3';
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
  dependencyPins: AppPackageDependencyPin[];
  nativeCapabilities: AppPackageNativeCapability;
  contractLock: AppPackageContractLock;
};

export type AppPackage = AppPackageV2 | AppPackageV3;

export type PackageValidation = { valid: true; package: AppPackage } | { valid: false; errors: string[] };
