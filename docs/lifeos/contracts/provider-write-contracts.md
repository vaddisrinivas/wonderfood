# Provider write contracts

## Google Sheets compare-and-set

`POST /providers/sheets/push` accepts optional `expected_version` and `expected_digest` fields.

- `expected_version` compares the managed row `version` value.
- `expected_digest` compares the canonical SHA-256 digest of the current provider row.
- When either value is stale, the server performs no write and returns HTTP `409` with `status: "conflict"` and a `conflict` object containing the expected value, current value, row number, and current digest/version where available.
- MCP update/archive calls automatically pass the stored Sheets `content_hash` as `expected_digest`, preventing a stale local record from overwriting a workbook edit.
- Create writes do not require a compare token; callers may still pass one, but it is ignored until a matching row exists.

Clients must refetch the provider record after a conflict, show the user the source change, and retry with a fresh digest/version. Never silently overwrite the workbook row.
