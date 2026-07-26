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
export type OperationCommitFailure = Readonly<{
  schemaVersion: 'wonder.operation-observer-failure.v1';
  phase: string;
  occurredAt: string;
  event: OperationCommitEvent;
  error: {
    name: string;
    message: string;
    stack?: string;
  };
}>;
export type OperationCommitFailureObserver = (failure: OperationCommitFailure) => void;

let observer: OperationCommitObserver | null = null;
let failureObserver: OperationCommitFailureObserver | null = null;

/** Install the pure reactive observer at the canonical successful-write boundary. */
export function setOperationCommitObserver(next: OperationCommitObserver | null): void {
  observer = next;
}

export function setOperationCommitFailureObserver(next: OperationCommitFailureObserver | null): void {
  failureObserver = next;
}

/** Observers are advisory; a failed proposal pass must never roll back a committed operation. */
export function notifyOperationCommit(event: OperationCommitEvent): void {
  try {
    observer?.(event);
  } catch (error) {
    const failure = toOperationCommitFailure(event, error);
    try {
      failureObserver?.(failure);
    } catch {
      // Failure capture must not affect the committed write boundary.
    }
  }
}

function toOperationCommitFailure(event: OperationCommitEvent, error: unknown): OperationCommitFailure {
  const err = error instanceof Error ? error : new Error(String(error));
  const phase = typeof (error as { phase?: unknown } | null)?.phase === 'string'
    ? String((error as { phase: string }).phase)
    : 'observer';
  return {
    schemaVersion: 'wonder.operation-observer-failure.v1',
    phase,
    occurredAt: new Date().toISOString(),
    event,
    error: {
      name: err.name || 'Error',
      message: err.message,
      ...(err.stack ? { stack: err.stack } : {}),
    },
  };
}
