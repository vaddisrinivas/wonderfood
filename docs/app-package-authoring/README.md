# App package authoring

V1 authoring is intentionally narrow.

AI can propose edits only to package source JSON files:

- `app.json`
- `collections/*.json`
- `queries/*.json`
- `screens/*.json`
- `rules/*.json`
- `workflows/*.json`
- `providers/*.json`
- `capabilities/*.json`
- `theme/*.json`
- `fixtures/*.json`
- `acceptance/*.json`

Flow:

1. Compute the current source revision.
2. AI proposes `utopia.authoring-change.v1`.
3. Runtime rejects stale revisions, code files, SQL, unsafe paths, and missing values.
4. Runtime applies the source patch to a clone.
5. Package compiler validates the clone.
6. User receives preview, semantic diff, package checksum, and risk labels.
7. A different approver creates an activation receipt.
8. Activation remains a separate kernel/runtime step.

Hard rules:

- AI cannot approve itself.
- AI cannot activate directly.
- AI cannot edit TypeScript, JavaScript, SQL, or arbitrary runtime code.
- AI cannot bypass the compiler.
- Risky source roots such as `providers`, `rules`, `workflows`, and `capabilities` are labeled for review.
