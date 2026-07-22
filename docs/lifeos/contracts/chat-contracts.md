# LifeOS chat contracts

## Send endpoint

### `POST /chat/send`

Request envelope (`Content-Type: application/json`):

- `thread_id` (string, required): canonical thread id.
- `conversation_id` (string, legacy alias): accepted for backward compatibility.
- `message` (string or object)
  - string: plain message text.
  - object: `{ id?: string, text: string }`.
- `plan_hint` (string, optional): explicit intent text to route to planner/executor.
- `domain_id` (string, optional): defaults to `food`.
- `idempotency_key` (string, optional): stable key to replay idempotent results.
- `previous_response_id` (string, optional): optional OpenAI previous response context.
- `retry_of` (string, optional): retry a prior user message.
- `mode` (enum `send|stream|preview`, optional): request mode hint.
- `preview` (boolean, optional): preview mode disables commit side effects in action execution.

Response:

- `conversation_id`
- `messages[]` (includes appended user + assistant message)
- `thread`
- `run` object
- optional `action`
  - `receipt` includes `source_ids`
  - `verification`
- optional `action_hints`
- `action.receipt` is the MCP canonical `lifeos.action-event.v1` subset and must preserve exact source references (`source_ids` and source citations) from retrieval.

## Stream endpoint

### `POST /chat/send/stream`

Same request envelope as `/chat/send`.

Server emits SSE frames (`data:` JSON) of type:

- `run.start` (`run_id`, `conversation_id`, optional `thread_id`)
- `token` (`run_id`, `conversation_id`, `delta`)
- `run.end` (`run_id`, `conversation_id`, `response`)
- `cache` (`conversation_id`, `response`) when idempotent replay is returned
- `error` (`error`)

Rules:

- Input accepts `mode: stream` or `preview` and the same envelope fields as `/chat/send`.
- `preview: true` disables mutating effects (executor may still validate and build policy responses without commits).
- `/chat/send` never emits synthetic/fixture model answers; model output may be unavailable and is reported in `warnings`.

## Retry

### `POST /chat/undo`

Input: `{ action_id, actor?, idempotency_key? }`

Output includes `undo_result` and idempotent replay semantics:

- successful first rollback executes action rollback
- repeated rollback with same `idempotency_key` returns replayed confirmation
