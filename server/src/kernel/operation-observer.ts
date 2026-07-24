export type OperationCommitEvent = {
  actionId: string;
  operationId: string;
  causeId: string;
  domain: string;
  recordId: string;
  before: unknown;
  after: unknown;
};

export type OperationCommitObserver = (event: OperationCommitEvent) => void;

let observer: OperationCommitObserver | null = null;

/** Install the pure reactive observer at the canonical successful-write boundary. */
export function setOperationCommitObserver(next: OperationCommitObserver | null): void {
  observer = next;
}

/** Observers are advisory; a failed proposal pass must never roll back a committed operation. */
export function notifyOperationCommit(event: OperationCommitEvent): void {
  try {
    observer?.(event);
  } catch {
    // Operation evidence is already committed. Observer failures are isolated.
  }
}
