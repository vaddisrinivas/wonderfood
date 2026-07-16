# Test Data Policy

Updated: 2026-07-16
Owner: Agent D / Quality

WonderFood tests must be safe to run, share, and archive. Test data is product evidence, so it must be deterministic, offline, and free of personal or secret information.

## Allowed

- Generic food names: eggs, spinach, rice, yogurt, berries.
- Generic merchants: `Generic Market`.
- Deterministic timestamps such as `2026-01-15T12:00:00Z`.
- Deterministic IDs such as `test-receipt-001`.
- Synthetic `content://wonderfood-test/...` URIs.
- Null nutrition values when a field is unknown.
- Small JSON fixtures under `app/src/test/resources/fixtures/**`.

## Forbidden

- Personal database rows or exports.
- Real receipts, addresses, store locations, phone numbers, loyalty IDs, payment card fragments, emails, or account names.
- Provider keys, bearer tokens, passwords, OAuth data, cookies, or endpoint credentials.
- Private screenshots or images from the user's device.
- Personal diet history, health notes, medical details, or private food preferences.
- Fixture values copied from private Notion, vault, chat, or local backup content.
- Network-dependent fixtures or tests that require a real AI/nutrition provider.

## Review Checklist

Before adding or updating a fixture:

1. Use only synthetic names, IDs, timestamps, and URIs.
2. Keep the fixture small enough for code review.
3. Preserve unknown values as `null`.
4. Add or update an offline test that loads the fixture.
5. Search the diff for credential words: `token`, `secret`, `password`, `authorization`, `bearer`, `api_key`.
6. Search the diff for personal identifiers before commit.

## Fixture Locations

- Nutrition fixtures: `app/src/test/resources/fixtures/nutrition/`
- Receipt fixtures: `app/src/test/resources/fixtures/receipts/`
- AI command-envelope fixtures: `app/src/test/resources/fixtures/command-envelopes/`
- Golden AI documentation fixtures: `docs/ai/fixtures/golden/`

The `docs/ai/fixtures/golden/` files are contract examples. Local test fixtures may be based on their shape, but they must stay generic and must not depend on production providers.
