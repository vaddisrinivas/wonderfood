# W1-AI Isolated AI SDK parity proof

## Scope
- Task: W1-AI Isolated AI SDK parity proof
- Agent: agt_RLlVPo6 (Product Integrator)
- Scope path: `spikes/ai-sdk/**`
- Base branch: `main`

## Baseline evidence
- Base SHA (main): `5f9b091c9667593f74e383554e59eb562211c4dd`
- Baseline commit at start: `114a0931a3d7099e9eaaad851317b26b794d4cab`
- Current branch HEAD after task: `<pending commit>`

## Package pinning
- `ai`: `7.0.37`
- `@ai-sdk/react`: `4.0.40`
- `@ai-sdk/openai`: `4.0.20`

## Diff tree
- Files added under spike:
  - `spikes/ai-sdk/package.json`
  - `spikes/ai-sdk/package-lock.json`
  - `spikes/ai-sdk/src/server/agent.ts`
  - `spikes/ai-sdk/src/server/model.ts`
  - `spikes/ai-sdk/src/server/stream-handler.ts`
  - `spikes/ai-sdk/src/client/chat-client.tsx`
  - `spikes/ai-sdk/src/client/approval.ts`
  - `spikes/ai-sdk/src/types/expo-fetch.d.ts`
  - `spikes/ai-sdk/tests/guard.test.ts`
  - `spikes/ai-sdk/scripts/validate-guards.mjs`
  - `spikes/ai-sdk/tsconfig.json`
  - `spikes/ai-sdk/REPORT.md`
  - `spikes/ai-sdk/deletion-inventory.md`

## Checks
- `npm run check`
  - Result: pass (typecheck + vitest)
- `npm run guard`
  - Result: pass

## Blocker
- none

## Merge risk
- Isolated spike only; no production imports/edits.
- Zero runtime mutation path changes in root/server.
- Medium-low risk: API-level pin drift should be revalidated at merge if model contracts change.

## Remaining scope
- Keep this spike proof as isolated proof-of-concept.
- Follow-up tasks must consume this proof for production migration and bounded deletion work.

## Ready to merge
- Yes
