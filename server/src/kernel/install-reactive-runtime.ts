import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { mkdirSync } from 'node:fs';
import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog } from '@/src/domain/catalog';
import { listRecords } from '../mcp/state';
import { setOperationCommitObserver } from './operation-observer';
import { createReactiveCycleObserver } from './reactive-observer';
import { createReactiveReceiptStore, parseReactiveReceiptStore, type ReactiveReceiptStore } from './reactive-receipts';
import { createReactiveOutboxStore, enqueueReactiveProposals, parseReactiveOutboxStore, type ReactiveOutboxStore } from './reactive-outbox';

const REACTIVE_RUNTIME_SCHEMA_VERSION = 'wonder.reactive-runtime.v1' as const;

type ReactiveRuntimeStore = Readonly<{
  schemaVersion: typeof REACTIVE_RUNTIME_SCHEMA_VERSION;
  receipts: ReactiveReceiptStore;
  outbox: ReactiveOutboxStore;
}>;

const legacyReceiptPath = process.env.LIFEOS_REACTIVE_RECEIPTS_PATH?.trim()
  || `${process.cwd()}/server-data/reactive-receipts.json`;
const defaultRuntimePath = process.env.LIFEOS_REACTIVE_RUNTIME_PATH?.trim()
  || `${process.cwd()}/server-data/reactive-runtime.json`;

function loadReceipts(path: string): ReactiveReceiptStore {
  if (!existsSync(path)) return createReactiveReceiptStore();
  return parseReactiveReceiptStore(readFileSync(path, 'utf8'));
}

function createReactiveRuntimeStore(): ReactiveRuntimeStore {
  return {
    schemaVersion: REACTIVE_RUNTIME_SCHEMA_VERSION,
    receipts: createReactiveReceiptStore(),
    outbox: createReactiveOutboxStore(),
  };
}

function parseRuntimeStore(serialized: string): ReactiveRuntimeStore {
  let value: unknown;
  try {
    value = JSON.parse(serialized);
  } catch {
    throw new Error('Reactive runtime store is not valid JSON.');
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Reactive runtime store is not an object.');
  }
  const row = value as Record<string, unknown>;
  if (row.schemaVersion !== REACTIVE_RUNTIME_SCHEMA_VERSION) {
    throw new Error('Reactive runtime store has an unsupported schema version.');
  }
  return {
    schemaVersion: REACTIVE_RUNTIME_SCHEMA_VERSION,
    receipts: parseReactiveReceiptStore(JSON.stringify(row.receipts)),
    outbox: parseReactiveOutboxStore(JSON.stringify(row.outbox)),
  };
}

function loadRuntimeStore(path: string): ReactiveRuntimeStore {
  if (existsSync(path)) return parseRuntimeStore(readFileSync(path, 'utf8'));
  if (existsSync(legacyReceiptPath)) {
    return {
      ...createReactiveRuntimeStore(),
      receipts: loadReceipts(legacyReceiptPath),
    };
  }
  return createReactiveRuntimeStore();
}

function writeRuntimeStore(path: string, store: ReactiveRuntimeStore): void {
  mkdirSync(dirname(path), { recursive: true });
  const tempPath = `${path}.tmp-${process.pid}`;
  writeFileSync(tempPath, JSON.stringify(store, null, 2), 'utf8');
  renameSync(tempPath, path);
}

/** Install the default manifest-backed observer at server startup. */
export function installReactiveRuntime(path = defaultRuntimePath): void {
  const manifest = loadCatalog().activeManifest;
  const { package: appPackage } = buildAppPackageFromManifest(manifest);
  let runtime = loadRuntimeStore(path);
  setOperationCommitObserver(createReactiveCycleObserver({
    package: appPackage,
    getRows: () => listRecords({ domain: manifest.id, includeArchived: true }) as unknown as Record<string, unknown>[],
    getReceiptStore: () => runtime.receipts,
    setReceiptStore: (next) => {
      runtime = { ...runtime, receipts: next };
      writeRuntimeStore(path, runtime);
    },
    commitCycle: ({ receipt, cycle, event }) => {
      const outbox = enqueueReactiveProposals(runtime.outbox, {
        cycle,
        event,
        proposalIds: receipt.newProposalIds,
      });
      runtime = {
        schemaVersion: REACTIVE_RUNTIME_SCHEMA_VERSION,
        receipts: receipt.store,
        outbox,
      };
      writeRuntimeStore(path, runtime);
    },
  }));
}
