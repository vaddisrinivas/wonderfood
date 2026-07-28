# Utopia Horizons 1 and 2 Checklist

## Status

- `BLOCKED`: authorization/dependency missing.
- `READY`: dependencies and ownership clear.
- `ACTIVE`: currently executing.
- `DONE`: exact acceptance gate passed.
- `PARKED`: retained outside Horizons 1-2.

## Authorization

- [x] Broader Utopia implementation is authorized.
- [x] Expo UI edits are authorized.
- [ ] `BLOCKED` Isolated worktree/branch or owned-diff manifest exists.
- [x] Exact base SHA and baseline evidence are recorded.

# Horizon 1: Useful Utopia V1

## H1-A AppPackage Authority

- [x] H1-00 Establish owned baseline.
- [x] H1-01 Freeze registry, compatibility, and diagnostic contracts.
- [x] H1-02 Register existing `wonder.app-package.v2/v3`; no redesign.
- [x] H1-03 Add shared Ajv adapter, one fixture corpus, RFC 8785 hashing, stable issues.
- [x] H1-04 Route compiler/runtime package validation through the adapter.
- [x] Delete duplicate package fixture corpus after parity.
- [x] `npm run check:schema-registry`
- [x] `npm run check:platform-day1`
- [x] Checkpoint H1-A passes without adding speculative schemas.

## H1-B Portable Isolated Apps

- [x] H1-05A Add AppInstallation/state schemas with persistence parity.
- [x] H1-05B Add InstallDescriptor schema with hostile-safe resolver.
- [ ] Resolver proves redirect-by-redirect DNS/IP policy, streaming limits, abort,
      final URL/addresses, CORS/proxy decision, content type, and checksum.
- [x] H1-06 Persist install intent; atomically activate installation/package/receipt.
- [x] Approval binds scope, hashes, actor, policy, expiry, nonce, and consumption.
- [x] H1-07A Add `/apps/[installationId]` route/provider.
- [x] H1-07B Scope core repository/runtime consumers; remove unsafe default fallbacks.
- [x] H1-07C Run core runtime adversarial isolation proof.
- [x] H1-08 Complete installer and local app library.
- [x] `npm run check:link-install`
- [x] `npm run check:installation-isolation-e2e`
- [x] Checkpoint H1-B passes on web and Android.

## H1-C Safe App Evolution

- [x] H1-09 Add MigrationPlan/snapshot/approval/receipt schemas and durable journal.
- [x] Journal records affected counts and full replay-bound approval fields.
- [x] H1-10 Apply declarative record changes and package activation atomically.
- [x] H1-11 Prove interrupted restart recovery and manual-review receipts.
- [x] H1-12A Add PackageSource schema with durable revisions.
- [x] H1-12B Build source browser and schema forms.
- [x] H1-13 Add AuthoringChange schema with deterministic model-fixture blank-app creation
      and bounded RFC 6902 patch proposals.
- [x] Structured-output/provider failure never mutates active source.
- [x] H1-14 Add diff, production preview, approval, activation, and source rollback domain APIs.
- [x] `npm run check:package-authoring`
- [x] `npm run check:package-compiler`
- [x] `npm run check:migrations`
- [x] `npm run check:package-migrations`
- [x] `npm run check:package-control-room-e2e`
- [x] Checkpoint H1-C passes.

## H1-D Local Vault and Distribution

- [ ] H1-15A Approve exact cross-platform AEAD/KDF/library/parameters and vectors.
- [x] H1-15B Implement versioned hostile-safe Vault envelope.
- [x] H1-16 Add local Vault preview/restore domain APIs with atomic restore behavior.
- [ ] Backup covers artifacts, source/revisions, records/relations, operations,
      migrations/snapshots/receipts, grants, and metadata.
- [x] H1-17 Add offline web picker, storage, preview, approval, restore UI; native stub fails closed.
- [ ] H1-18 Build deterministic GitHub Action, immutable Release assets, and Pages index.
- [x] Label checksum-only packages “unsigned integrity.”
- [x] H1-19 Add registry picker and immutable provenance metadata.
- [x] `npm run check:vault`
- [x] `npm run check:registry-distribution`
- [x] `npm run check:registry-scale`

## H1 Acceptance

- [x] H1-20 deterministic `npm run check:utopia-h1-e2e`: clean DB -> fixture AI output
      -> compile -> install twice -> mutate -> migrate -> rollback -> export ->
      delete -> restore -> local immutable-registry reinstall.
- [ ] Separate live proof: real model creation.
- [ ] Separate live proof: immutable GitHub Release publish/install round-trip.
- [x] `npm run config:validate`
- [x] `npm run typecheck`
- [x] `npm run doctor`
- [x] `npm run export:web`
- [x] `npm run export:android`
- [x] `npm run phase3:check:chat-send`
- [x] `npm run phase3:check:chat-rollback-idempotency`
- [x] `npm run check:app-installation-foundation`
- [x] `npm run check:app-installation-data`
- [x] `npm run check:app-installation-secondary`
- [x] Two-installation isolation holds through every deterministic stage.
- [x] Evidence separates focused, aggregate, device, and release status.
- [ ] Exact commit/owned diff recorded; no open/partial P0/P1.
- [ ] User accepts Horizon 1 before Horizon 2.

# Horizon 2: Connected Utopia

## Cloud Decision

- [ ] `BLOCKED` H2-00 Approve cloud ADR and threat model.
- [ ] Freeze server-compromise model, metadata exposure, cryptographic deletion,
      key hierarchy, DEK wrapping, recovery, rotation, revocation, and loss.
- [ ] Freeze client-verifiable device-key binding and server key-substitution defense.
- [ ] Run provider-neutral cloud spike against local dev, OIDC, PostgreSQL RLS,
      S3-compatible storage, exportability, outage, and portability gates.
- [x] Run static import check rejecting cloud-provider SDKs in kernel/mobile.
- [x] `npm run check:cloud-portability`
- [ ] Prove mobile uses Utopia API endpoints, not direct table/object mutation
      or direct object-store mutation.
- [ ] Run exit test restoring metadata, blobs, receipts, and audit rows into a
      non-provider stack.
- [ ] Select standards-compatible components and adapters with evidence.

## H2-A Contracts, Plugins, Identity

- [ ] H2-01 Assign each remaining schema to a real consumer vertical.
- [x] H2-03 Add plugin schemas with runtime/build/server resolver consumers.
- [x] H2-04 Prove plugin fallback and kernel-only mutation.
- [x] `npm run check:plugin-compatibility-expanded`
- [x] H2-05 Add account/session/device schemas and proof-of-possession contract.
- [ ] H2-06 Add PostgreSQL composite tenant keys, RLS, transaction-local context,
      pool reset proof, hostile direct-query tests, backup/restore.
- [x] `npm run check:cloud-migrations`
- [ ] H2-07 Use OIDC authorization code + PKCE, strict redirects/state, JWKS
      rotation, refresh-family reuse detection, secure storage, device proof.
- [x] `npm run check:account-auth`
- [x] H2-08 Add optional account/device UI without harming local-only state.
- [x] Checkpoint H2-A passes for deterministic local/provider-neutral foundation.

## H2-B Encrypted Cloud Vault

- [x] H2-09 Add atomic ciphertext metadata/object publication, opaque keys,
      constrained presigned requests, quota-race/lifecycle/orphan handling.
- [x] H2-10 Add deterministic backup/list/download/local preview/restore and DEK wrapping.
- [x] Verify device-key binding and reject old-valid-backup rollback.
- [x] H2-11 Add revoke, rotation/rewrap, old-backup policy, lost-key state,
      export, deletion, and receipts.
- [x] `npm run check:cloud-vault-storage`
- [x] `npm run check:cloud-vault`
- [x] `npm run check:cloud-data-controls`

## H2-C Collaboration

- [x] H2-12A Add roles/invites, operation permission matrix, last-owner protection,
      identity-bound acceptance, atomic transitions, membership epochs.
- [x] H2-12B Implement visibility/authorization/revocation policy before append.
- [x] H2-13 Authorize and append stream operation in one transaction; freeze receipt.
- [x] H2-14 Implement deterministic push/pull/kernel apply with checkpoint recovery model.
- [x] H2-15 Integrate conflict receipts and end-to-end revocation.
- [ ] H2-16 Add collaboration UI.
- [x] `npm run check:collaboration-membership`
- [x] `npm run check:collaboration-policy`
- [x] `npm run check:collaboration-stream`
- [x] `npm run check:collaboration`
- [x] Checkpoint H2-C deterministic collaboration core passes.

## H2-D App Compositions

- [x] H2-17 Add capability/grant schemas and per-invocation policy revalidation.
- [x] H2-18 Build read-only composition runtime.
- [x] H2-19 Prove deterministic three-installation composition flow.
- [x] H2-20 Add proposal-only writes with hash/capability/policy/expiry/replay binding.
- [x] Approval consumption, target write, and receipt persist in one transaction.
- [x] `npm run check:composition-contracts`
- [x] `npm run check:app-compositions`
- [x] `npm run check:composition-writes`

## H2 Acceptance

- [ ] H2-02 Finish schema convergence only after all consumer verticals.
- [x] `npm run check:schema-registry-full`
- [x] H2-21 Run `npm run check:utopia-connected`.
- [x] Longitudinal clean-state journey covers enrollment, backup, invite, offline
      sync, convergence, revocation, composition read/write, deletion, signed-out fallback.
- [x] Horizon 1 still passes offline and signed out.
- [ ] PostgreSQL restore, storage outage/orphan cleanup, lost recovery key,
      deletion verification, load/backpressure, and revoke/invite races pass.
- [x] Two-account/two-device Vault, collaboration, and composition proof passes.
- [ ] Evidence names exact commit; no open/partial P0/P1.
- [ ] User accepts Horizon 2 before Horizon 3.

# Reduction Track

- [ ] Consolidate root/server package fixture corpus: about 955 fixture LOC.
- [ ] Remove duplicate structural validators after parity: about 520-740 TS LOC.
- [ ] Replace regex/template AI authoring with structured output + RFC 6902:
      about 900-1,200 LOC.
- [ ] Consolidate canonical hashing/pointer/patch helpers: about 70-250 LOC.
- [ ] Modularize server routes through Hono: about 450-700 LOC.
- [ ] Consolidate provider replay/refetch pipeline: about 300-550 LOC.
- [ ] Evaluate one JSON Render contract plus V3 adapter: about 650-950 LOC.
- [ ] Avoid custom registry/publisher backend via GitHub: about 700-1,400 future LOC.
- [ ] Avoid platform cloud lock-in through OIDC/PostgreSQL/S3 adapters and exit tests.
- [ ] Never replace kernel authority, approvals, migrations, operation policy, or receipts.

# Horizon 3: Parked

- [ ] `PARKED` Public marketplace and publisher portal.
- [ ] `PARKED` Signing/key rotation/update service.
- [ ] `PARKED` Commercial control plane.
- [ ] `PARKED` 500-app corpus and production-scale hardening.
