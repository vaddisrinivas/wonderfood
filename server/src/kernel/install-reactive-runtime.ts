import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { mkdirSync } from 'node:fs';
import { buildAppPackageFromManifest } from '@/src/domain/app-package-bridge';
import { loadCatalog } from '@/src/domain/catalog';
import { listRecords } from '../mcp/state';
import { setOperationCommitObserver } from './operation-observer';
import { createReactiveCycleObserver } from './reactive-observer';
import { createReactiveReceiptStore, parseReactiveReceiptStore, serializeReactiveReceiptStore, type ReactiveReceiptStore } from './reactive-receipts';

const defaultReceiptPath = process.env.LIFEOS_REACTIVE_RECEIPTS_PATH?.trim()
  || `${process.cwd()}/server-data/reactive-receipts.json`;

function loadReceipts(path: string): ReactiveReceiptStore {
  if (!existsSync(path)) return createReactiveReceiptStore();
  try {
    return parseReactiveReceiptStore(readFileSync(path, 'utf8'));
  } catch {
    // Corrupt receipts must not prevent the server from serving; start a new
    // ledger and let the next operation establish fresh evidence.
    return createReactiveReceiptStore();
  }
}

/** Install the default manifest-backed observer at server startup. */
export function installReactiveRuntime(path = defaultReceiptPath): void {
  const manifest = loadCatalog().activeManifest;
  const { package: appPackage } = buildAppPackageFromManifest(manifest);
  let receipts = loadReceipts(path);
  setOperationCommitObserver(createReactiveCycleObserver({
    package: appPackage,
    getRows: () => listRecords({ domain: manifest.id, includeArchived: true }) as unknown as Record<string, unknown>[],
    getReceiptStore: () => receipts,
    setReceiptStore: (next) => {
      receipts = next;
      mkdirSync(dirname(path), { recursive: true });
      writeFileSync(path, serializeReactiveReceiptStore(receipts), 'utf8');
    },
  }));
}
