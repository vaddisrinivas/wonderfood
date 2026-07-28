# ADR 0004: Utopia Cloud Portability

Status: proposed

Date: 2026-07-28

## Decision

Horizon 2 cloud work uses replaceable provider adapters around the Utopia
kernel:

- `AuthProvider`: OIDC authorization code + PKCE, JWKS validation, refresh
  family tracking, and external-subject to Utopia-account mapping.
- `MetadataStore`: PostgreSQL tables, ordinary SQL migrations, tenant composite
  keys, transaction-local authorization context, and RLS hostile-query tests.
- `BlobStore`: opaque object keys, S3-compatible operations where possible,
  atomic metadata/object publication, resumable download, and orphan cleanup.

The product kernel and mobile runtime must not import a cloud-provider SDK.
Repository checks enforce that boundary. Mobile clients call Utopia API
endpoints. They do not mutate provider tables or objects directly.

Vault plaintext and keys stay local. Cloud storage receives ciphertext and
metadata only.

## Acceptance Gates

- Local dev stack runs without a hosted vendor account.
- OIDC authority stays external-subject to Utopia-account mapping only; tenant
  authority stays inside Utopia contracts.
- RLS tests prove cross-account direct queries fail.
- Pool reset tests prove tenant context does not leak between requests.
- Storage outage tests prove no dangling trusted metadata after object failure.
- Export/restore moves accounts, devices, metadata, blobs, receipts, and audit
  rows into a non-provider stack.
- Static import scan rejects provider SDK imports from kernel and mobile runtime.

## Non-Goals

- Public marketplace hosting.
- Provider-owned operation sequencing.
- Provider-owned approval, migration, receipt, or collaboration policy.
- Direct mobile access to cloud tables or bucket objects.
