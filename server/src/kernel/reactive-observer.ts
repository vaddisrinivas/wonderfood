import type { AppPackageV2 } from './package';
import { recordReactiveCycle, type ReactiveReceiptStore, type RecordReactiveCycleResult } from './reactive-receipts';
import { runReactiveCycle, type ReactiveCycleResult } from './reactive-cycle';
import type { OperationCommitEvent, OperationCommitObserver } from './operation-observer';

export type ReactiveObserverConfig = {
  package: AppPackageV2;
  getRows: () => readonly Record<string, unknown>[];
  getReceiptStore: () => ReactiveReceiptStore;
  setReceiptStore: (store: ReactiveReceiptStore) => void;
  commitCycle?: (input: {
    receipt: RecordReactiveCycleResult;
    cycle: ReactiveCycleResult;
    event: OperationCommitEvent;
  }) => void;
  onNewProposals?: (proposalIds: readonly string[], cycle: ReactiveCycleResult, event: OperationCommitEvent) => void;
};

/** Adapt committed operations to the pure reactive cycle and receipt ledger. */
export function createReactiveCycleObserver(config: ReactiveObserverConfig): OperationCommitObserver {
  return (event) => {
    const afterRows = [...config.getRows()];
    const beforeRows = afterRows.filter((row) => row.id !== event.recordId);
    if (event.before && typeof event.before === 'object') {
      beforeRows.push(event.before as Record<string, unknown>);
    }
    const cycle = runReactiveCycle({
      package: config.package,
      beforeRows,
      afterRows,
      event: { kind: 'operation', id: event.operationId },
      data: event,
      causeId: event.causeId,
    });
    const receipt = recordReactiveCycle(config.getReceiptStore(), {
      cycleId: cycle.cycleId,
      proposals: cycle.proposals,
    });
    if (config.commitCycle) {
      config.commitCycle({ receipt, cycle, event });
      return;
    }
    config.setReceiptStore(receipt.store);
    if (receipt.newProposalIds.length && config.onNewProposals) {
      config.onNewProposals(receipt.newProposalIds, cycle, event);
    }
  };
}
